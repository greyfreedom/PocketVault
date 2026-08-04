package com.turisla.hellopocket.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File
import java.nio.file.Files

class VaultFileTransactionTest {

    @Test
    fun commitReplacesEveryFileAndSupportsNestedAttachments() {
        withTempDirectory { directory ->
            val first = File(directory, "passwords.dat").apply { writeText("old-passwords") }
            val second = File(directory, "attachments/item.dat").apply {
                parentFile?.mkdirs()
                writeText("old-attachment")
            }
            val firstPending = VaultFileTransaction.createPendingFile(first).apply { writeText("new-passwords") }
            val secondPending = VaultFileTransaction.createPendingFile(second).apply { writeText("new-attachment") }

            VaultFileTransaction.commit(
                directory,
                listOf(
                    VaultFileTransaction.PendingWrite(first, firstPending),
                    VaultFileTransaction.PendingWrite(second, secondPending)
                )
            )

            assertEquals("new-passwords", first.readText())
            assertEquals("new-attachment", second.readText())
            assertFalse(File(directory, ".vault_transaction").exists())
        }
    }

    @Test
    fun missingPendingFileCannotModifyExistingVault() {
        withTempDirectory { directory ->
            val target = File(directory, "passwords.dat").apply { writeText("old") }
            val missing = File(directory, ".passwords.dat.pending-missing")

            assertThrows(IllegalArgumentException::class.java) {
                VaultFileTransaction.commit(
                    directory,
                    listOf(VaultFileTransaction.PendingWrite(target, missing))
                )
            }
            assertEquals("old", target.readText())
        }
    }

    @Test
    fun recoveryRollsBackACommitInterruptedBetweenFileMoves() {
        withTempDirectory { directory ->
            val first = File(directory, "passwords.dat")
            val second = File(directory, "categories.dat")
            val firstPending = File(directory, ".passwords.dat.pending-test")
            val secondPending = File(directory, ".categories.dat.pending-test")
            val firstBackup = File(directory, ".passwords.dat.backup-test")
            val secondBackup = File(directory, ".categories.dat.backup-test")

            firstBackup.writeText("old-passwords")
            secondBackup.writeText("old-categories")
            first.writeText("new-passwords")
            secondPending.writeText("new-categories")
            File(directory, ".vault_transaction").writeText(
                "test\n" +
                    "passwords.dat|${firstPending.name}|${firstBackup.name}|1\n" +
                    "categories.dat|${secondPending.name}|${secondBackup.name}|1\n"
            )

            VaultFileTransaction.recover(directory)

            assertEquals("old-passwords", first.readText())
            assertEquals("old-categories", second.readText())
            assertFalse(firstPending.exists())
            assertFalse(secondPending.exists())
            assertFalse(File(directory, ".vault_transaction").exists())
        }
    }

    @Test
    fun recoveryFinishesCleanupForCommittedTransaction() {
        withTempDirectory { directory ->
            val target = File(directory, "passwords.dat").apply { writeText("new-passwords") }
            val pending = File(directory, ".passwords.dat.pending-test")
            val backup = File(directory, ".passwords.dat.backup-test").apply { writeText("old-passwords") }
            File(directory, ".vault_transaction").writeText(
                "COMMITTED|test\n" +
                    "passwords.dat|${pending.name}|${backup.name}|1\n"
            )

            VaultFileTransaction.recover(directory)

            assertEquals("new-passwords", target.readText())
            assertFalse(backup.exists())
            assertFalse(File(directory, ".vault_transaction").exists())
        }
    }

    private fun withTempDirectory(block: (File) -> Unit) {
        val directory = Files.createTempDirectory("vault-transaction-test").toFile()
        try {
            block(directory)
        } finally {
            directory.deleteRecursively()
        }
    }
}
