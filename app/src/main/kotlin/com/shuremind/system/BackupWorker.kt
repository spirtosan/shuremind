package com.shuremind.system

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.shuremind.BuildConfig
import com.shuremind.ShuRemindApplication
import com.shuremind.data.backup.ExportEngine
import com.shuremind.data.repo.AutoBackupScheduling
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.util.concurrent.TimeUnit

/** D-32: daily auto-backup, a sibling to [HousekeepingWorker] (not folded into it — independent enable/disable lifecycle). */
class BackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as ShuRemindApplication).container
        val settings = container.backupSettingsRepository.settings.first()
        val folderUri = settings.folderUri
        if (!AutoBackupScheduling.shouldBeEnqueued(settings.autoBackupEnabled, folderUri) || folderUri == null) {
            return Result.success()
        }
        if (!container.backupFileWriter.hasPersistedPermission(folderUri)) {
            Log.w(TAG, "Auto-backup folder permission no longer held; skipping this run")
            return Result.success()
        }
        val snapshot = container.backupRepository.buildSnapshot()
        val file = ExportEngine.buildExportFile(snapshot, BuildConfig.VERSION_NAME, System.currentTimeMillis())
        val json = ExportEngine.toJson(file)
        container.backupFileWriter.writeToFolder(folderUri, json)
            .onFailure { Log.w(TAG, "Auto-backup write failed", it) }
        return Result.success()
    }

    companion object {
        private const val TAG = "BackupWorker"
        private const val UNIQUE_WORK_NAME = "auto_backup"
        private val INTERVAL: Duration = Duration.ofDays(1)

        fun enqueue(context: Context) {
            val request = PeriodicWorkRequestBuilder<BackupWorker>(INTERVAL.toMillis(), TimeUnit.MILLISECONDS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
        }
    }
}
