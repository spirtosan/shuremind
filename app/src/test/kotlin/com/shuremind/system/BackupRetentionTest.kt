package com.shuremind.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupRetentionTest {

    @Test
    fun `nine backups keeps only the newest seven`() {
        val names = (1..9).map { day -> "shuremind-backup-202607%02d-090000.json".format(day) }

        val toDelete = BackupRetention.namesToDelete(names)

        assertEquals(2, toDelete.size)
        assertEquals(
            setOf("shuremind-backup-20260701-090000.json", "shuremind-backup-20260702-090000.json"),
            toDelete
        )
    }

    @Test
    fun `fewer than the retention limit deletes nothing`() {
        val names = (1..5).map { day -> "shuremind-backup-202607%02d-090000.json".format(day) }

        assertTrue(BackupRetention.namesToDelete(names).isEmpty())
    }

    @Test
    fun `non-matching file names are ignored`() {
        val names = listOf("notes.txt", "shuremind-backup-20260701-090000.json", "random.json")

        assertTrue(BackupRetention.namesToDelete(names).isEmpty())
    }

    @Test
    fun `custom retention limit is respected`() {
        val names = (1..5).map { day -> "shuremind-backup-202607%02d-090000.json".format(day) }

        val toDelete = BackupRetention.namesToDelete(names, keep = 2)

        assertEquals(3, toDelete.size)
    }
}
