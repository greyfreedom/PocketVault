package com.turisla.hellopocket.data

import java.io.File

internal data class BackupRetentionEntry(
    val file: File,
    val sizeBytes: Long = file.length(),
    val lastModifiedMs: Long = file.lastModified(),
)

internal object BackupRetentionPolicy {

    /**
     * 返回应删除的备份。受保护的最新备份始终保留，即使它本身已超过容量上限。
     */
    fun selectForDeletion(
        entries: List<BackupRetentionEntry>,
        protectedBackup: File,
        maxCount: Int,
        maxTotalBytes: Long,
    ): List<File> {
        require(maxCount > 0)
        require(maxTotalBytes >= 0L)

        val protectedPath = protectedBackup.absolutePath
        val orderedEntries = entries.sortedWith(
            compareByDescending<BackupRetentionEntry> {
                it.file.absolutePath == protectedPath
            }.thenByDescending { it.lastModifiedMs }
        )

        var retainedCount = 0
        var retainedBytes = 0L
        var retentionLimitReached = false
        return buildList {
            orderedEntries.forEach { entry ->
                val sizeBytes = entry.sizeBytes.coerceAtLeast(0L)
                val isProtected = entry.file.absolutePath == protectedPath
                val fitsCount = retainedCount < maxCount
                val fitsSize = sizeBytes <= maxTotalBytes - retainedBytes.coerceAtMost(maxTotalBytes)

                if (isProtected || (!retentionLimitReached && fitsCount && fitsSize)) {
                    retainedCount++
                    retainedBytes = (retainedBytes + sizeBytes).coerceAtMost(Long.MAX_VALUE)
                } else {
                    retentionLimitReached = true
                    add(entry.file)
                }
            }
        }
    }
}
