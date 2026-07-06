package com.shuremind.data.repo

import kotlinx.coroutines.flow.Flow

/**
 * D-32: device-specific auto-backup settings (deliberately NOT part of [AppSettings]/export per
 * D-30 — the folder URI and the toggle describe this device, not the data).
 */
data class BackupSettings(
    val autoBackupEnabled: Boolean = false,
    /** SAF tree Uri string, or null if no folder has been picked yet. */
    val folderUri: String? = null
)

/** UI/ViewModels talk only to this repo, never DataStore directly. Fakeable for ViewModel unit tests. */
interface BackupSettingsRepository {

    val settings: Flow<BackupSettings>

    suspend fun setAutoBackupEnabled(enabled: Boolean)

    suspend fun setFolderUri(uri: String?)
}

/**
 * Repository-level hook (mirrors [ScheduleChangeNotifier]): a write that can change whether
 * [com.shuremind.system.BackupWorker] should be enqueued triggers this so AppContainer can wire the
 * real enqueue/cancel call without BackupSettingsRepository depending on `system`/WorkManager directly.
 */
fun interface AutoBackupSchedulingNotifier {
    suspend fun onBackupSettingsChanged(enabled: Boolean, folderUri: String?)

    companion object {
        val NONE = AutoBackupSchedulingNotifier { _, _ -> }
    }
}

/** D-32: pure decision of whether the daily auto-backup worker should be scheduled right now. */
object AutoBackupScheduling {
    fun shouldBeEnqueued(enabled: Boolean, folderUri: String?): Boolean = enabled && !folderUri.isNullOrBlank()
}
