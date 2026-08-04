package com.turisla.hellopocket.data

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupRetentionPolicyTest {

    @Test
    fun `deletes oldest backups beyond count limit`() {
        val entries = (1L..7L).map { timestamp ->
            BackupRetentionEntry(
                file = File("backup-$timestamp.hpb"),
                sizeBytes = 10L,
                lastModifiedMs = timestamp,
            )
        }

        val deletions = BackupRetentionPolicy.selectForDeletion(
            entries = entries,
            protectedBackup = File("backup-7.hpb"),
            maxCount = 5,
            maxTotalBytes = 1_000L,
        )

        assertEquals(listOf("backup-2.hpb", "backup-1.hpb"), deletions.map(File::getName))
    }

    @Test
    fun `deletes older backups when total size would exceed limit`() {
        val entries = listOf(
            BackupRetentionEntry(File("new.hpb"), sizeBytes = 60L, lastModifiedMs = 3L),
            BackupRetentionEntry(File("middle.hpb"), sizeBytes = 50L, lastModifiedMs = 2L),
            BackupRetentionEntry(File("old.hpb"), sizeBytes = 30L, lastModifiedMs = 1L),
        )

        val deletions = BackupRetentionPolicy.selectForDeletion(
            entries = entries,
            protectedBackup = File("new.hpb"),
            maxCount = 5,
            maxTotalBytes = 100L,
        )

        assertEquals(listOf("middle.hpb", "old.hpb"), deletions.map(File::getName))
    }

    @Test
    fun `always retains protected backup even when it exceeds size limit`() {
        val protected = File("protected.hpb")
        val entries = listOf(
            BackupRetentionEntry(File("newer-by-time.hpb"), sizeBytes = 1L, lastModifiedMs = 10L),
            BackupRetentionEntry(protected, sizeBytes = 200L, lastModifiedMs = 1L),
        )

        val deletions = BackupRetentionPolicy.selectForDeletion(
            entries = entries,
            protectedBackup = protected,
            maxCount = 5,
            maxTotalBytes = 100L,
        )

        assertTrue(protected !in deletions)
        assertEquals(listOf("newer-by-time.hpb"), deletions.map(File::getName))
    }
}
