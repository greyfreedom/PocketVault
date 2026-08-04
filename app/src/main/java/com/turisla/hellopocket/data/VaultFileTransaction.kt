package com.turisla.hellopocket.data

import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * 保险库文件事务工具。
 *
 * 所有 pending、backup 和 journal 都位于同一文件系统，确保切换时可以使用原子 rename。
 * journal 存在时表示上一次提交未完成；恢复逻辑会保守地回滚到旧版本。
 */
internal object VaultFileTransaction {

    private const val JOURNAL_FILE_NAME = ".vault_transaction"
    private const val DIRECTORY_JOURNAL_PREFIX = ".vault_directory_transaction_"

    data class PendingWrite(val target: File, val pending: File)

    fun createPendingFile(target: File): File {
        target.parentFile?.mkdirs()
        return File(target.parentFile, ".${target.name}.pending-${UUID.randomUUID()}")
    }

    fun syncFile(file: File) {
        FileOutputStream(file, true).use { output ->
            output.fd.sync()
        }
    }

    fun atomicWriteText(target: File, content: String) {
        val pending = createPendingFile(target)
        try {
            FileOutputStream(pending).use { output ->
                output.write(content.toByteArray(Charsets.UTF_8))
                output.fd.sync()
            }
            atomicMove(pending, target)
        } finally {
            pending.delete()
        }
    }

    fun commit(directory: File, writes: List<PendingWrite>) {
        require(writes.isNotEmpty()) { "Vault transaction must contain at least one file" }
        require(writes.all { isDescendant(directory, it.target) && isDescendant(directory, it.pending) }) {
            "All transaction files must stay inside the vault directory"
        }
        require(writes.all { it.pending.isFile }) { "Pending vault file is missing" }

        recover(directory)

        val transactionId = UUID.randomUUID().toString()
        val entries = writes.map { write ->
            val targetPath = relativePath(directory, write.target)
            val pendingPath = relativePath(directory, write.pending)
            val backup = File(write.target.parentFile, ".${write.target.name}.backup-$transactionId")
            TransactionEntry(
                targetName = targetPath,
                pendingName = pendingPath,
                backupName = relativePath(directory, backup),
                existed = write.target.exists()
            )
        }
        val journal = File(directory, JOURNAL_FILE_NAME)
        fun journalText(state: String) = buildString {
            appendLine("$state|$transactionId")
            entries.forEach { entry ->
                append(entry.targetName)
                append('|')
                append(entry.pendingName)
                append('|')
                append(entry.backupName)
                append('|')
                appendLine(if (entry.existed) "1" else "0")
            }
        }
        atomicWriteText(journal, journalText("PREPARED"))

        try {
            entries.forEach { entry ->
                if (entry.existed) {
                    atomicMove(File(directory, entry.targetName), File(directory, entry.backupName))
                }
            }
            entries.forEach { entry ->
                atomicMove(File(directory, entry.pendingName), File(directory, entry.targetName))
            }

            // 所有目标都切换完成后先持久化 COMMITTED，再清理备份。
            atomicWriteText(journal, journalText("COMMITTED"))
            entries.forEach { File(directory, it.backupName).delete() }
            journal.delete()
        } catch (error: Exception) {
            rollback(directory, entries)
            journal.delete()
            throw error
        } finally {
            entries.forEach { File(directory, it.pendingName).delete() }
        }
    }

    fun recover(directory: File) {
        val journal = File(directory, JOURNAL_FILE_NAME)
        if (!journal.isFile) return

        val journalData = parseJournal(directory, journal)
        val entries = journalData.entries
        if (journalData.committed) {
            require(entries.all { File(directory, it.targetName).exists() }) {
                "Committed vault transaction is missing a target file"
            }
            entries.forEach {
                File(directory, it.backupName).deleteRecursively()
                File(directory, it.pendingName).deleteRecursively()
            }
            journal.delete()
            return
        }

        rollback(directory, entries)
        journal.delete()
    }

    fun replaceDirectory(current: File, staged: File) {
        require(staged.isDirectory) { "Staged vault directory is missing" }
        require(current.parentFile?.canonicalFile == staged.parentFile?.canonicalFile) {
            "Directory replacement must stay on the same filesystem"
        }

        val parent = current.parentFile ?: error("Vault directory has no parent")
        val transactionId = UUID.randomUUID().toString()
        val backup = File(parent, ".${current.name}.backup-$transactionId")
        val journal = File(parent, "$DIRECTORY_JOURNAL_PREFIX$transactionId")
        atomicWriteText(
            journal,
            listOf(current.name, staged.name, backup.name, if (current.exists()) "1" else "0")
                .joinToString("\n")
        )

        val hadCurrent = current.exists()
        try {
            if (hadCurrent) atomicMove(current, backup)
            atomicMove(staged, current)
            backup.deleteRecursively()
            journal.delete()
        } catch (error: Exception) {
            if (current.exists() && backup.exists()) current.deleteRecursively()
            if (backup.exists()) atomicMove(backup, current)
            journal.delete()
            throw error
        }
    }

    fun recoverDirectoryTransactions(parent: File, directoryName: String) {
        parent.listFiles { file -> file.name.startsWith(DIRECTORY_JOURNAL_PREFIX) }?.forEach { journal ->
            val lines = journal.readLines()
            if (lines.size != 4 || lines[0] != directoryName) {
                journal.delete()
                return@forEach
            }

            val current = safeChild(parent, lines[0])
            val staged = safeChild(parent, lines[1])
            val backup = safeChild(parent, lines[2])
            val hadCurrent = lines[3] == "1"

            when {
                backup.exists() -> {
                    current.deleteRecursively()
                    atomicMove(backup, current)
                }
                current.exists() -> Unit // 新目录已完整激活，提交完成。
                hadCurrent -> error("Vault directory transaction cannot be recovered")
                staged.exists() -> atomicMove(staged, current)
            }
            staged.deleteRecursively()
            journal.delete()
        }
    }

    private fun rollback(directory: File, entries: List<TransactionEntry>) {
        entries.asReversed().forEach { entry ->
            val target = File(directory, entry.targetName)
            val pending = File(directory, entry.pendingName)
            val backup = File(directory, entry.backupName)

            if (backup.exists()) {
                target.deleteRecursively()
                atomicMove(backup, target)
            } else if (!entry.existed && !pending.exists()) {
                // 原目标不存在且 pending 已移动，删除本次事务创建的新文件。
                target.deleteRecursively()
            }
            pending.deleteRecursively()
        }
    }

    private fun parseJournal(directory: File, journal: File): JournalData {
        val lines = journal.readLines()
        require(lines.size >= 2) { "Malformed vault transaction journal" }
        val committed = lines.first().startsWith("COMMITTED|")
        val entries = lines.drop(1).filter { it.isNotBlank() }.map { line ->
            val parts = line.split('|')
            require(parts.size == 4) { "Malformed vault transaction entry" }
            parts.take(3).forEach { safeDescendant(directory, it) }
            TransactionEntry(parts[0], parts[1], parts[2], parts[3] == "1")
        }
        return JournalData(committed, entries)
    }

    private fun safeChild(parent: File, name: String): File {
        require(name.isNotBlank() && name != "." && name != "..") { "Invalid transaction file name" }
        val file = File(parent, name).canonicalFile
        require(file.parentFile == parent.canonicalFile) { "Transaction path escaped its parent" }
        return file
    }

    private fun safeDescendant(parent: File, relativePath: String): File {
        require(relativePath.isNotBlank() && '|' !in relativePath) { "Invalid transaction path" }
        val parentPath = parent.canonicalFile.path + File.separator
        val file = File(parent, relativePath).canonicalFile
        require(file.path.startsWith(parentPath) && file != parent.canonicalFile) {
            "Transaction path escaped its parent"
        }
        return file
    }

    private fun isDescendant(parent: File, file: File): Boolean {
        val parentPath = parent.canonicalFile.path + File.separator
        val filePath = file.canonicalFile.path
        return filePath.startsWith(parentPath)
    }

    private fun relativePath(parent: File, file: File): String {
        require(isDescendant(parent, file)) { "File is outside transaction directory" }
        val parentPath = parent.canonicalFile.path + File.separator
        return file.canonicalFile.path.removePrefix(parentPath)
    }

    private fun atomicMove(source: File, target: File) {
        target.parentFile?.mkdirs()
        // Android/Linux 的 File.renameTo 最终使用同文件系统 rename(2)，替换目录项是原子的；
        // 所有 transaction 文件都被强制放在同一个保险库目录/父目录中。
        check(source.renameTo(target)) {
            "Atomic filesystem rename failed for vault transaction"
        }
    }

    private data class TransactionEntry(
        val targetName: String,
        val pendingName: String,
        val backupName: String,
        val existed: Boolean
    )

    private data class JournalData(
        val committed: Boolean,
        val entries: List<TransactionEntry>
    )
}
