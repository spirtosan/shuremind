package com.shuremind.data.repo

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoBackupSchedulingTest {

    @Test
    fun `enqueued only when enabled and a folder is set`() {
        assertTrue(AutoBackupScheduling.shouldBeEnqueued(enabled = true, folderUri = "content://tree/abc"))
    }

    @Test
    fun `not enqueued when disabled even with a folder set`() {
        assertFalse(AutoBackupScheduling.shouldBeEnqueued(enabled = false, folderUri = "content://tree/abc"))
    }

    @Test
    fun `not enqueued when enabled but no folder set`() {
        assertFalse(AutoBackupScheduling.shouldBeEnqueued(enabled = true, folderUri = null))
    }

    @Test
    fun `not enqueued when folder uri is blank`() {
        assertFalse(AutoBackupScheduling.shouldBeEnqueued(enabled = true, folderUri = "  "))
    }

    @Test
    fun `toggling folder away while enabled transitions to not enqueued`() {
        assertTrue(AutoBackupScheduling.shouldBeEnqueued(enabled = true, folderUri = "content://tree/abc"))
        assertFalse(AutoBackupScheduling.shouldBeEnqueued(enabled = true, folderUri = null))
    }
}
