package com.shuremind.system

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.shuremind.ShuRemindApplication
import java.time.Duration
import java.util.concurrent.TimeUnit

/** Daily housekeeping (D-07/D-10): runs [RecomputeAndRearm] so recovery/re-arm doesn't depend solely on boot/open/alarm. */
class HousekeepingWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as ShuRemindApplication).container
        container.recomputeAndRearm.run()
        return Result.success()
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "housekeeping"
        private val INTERVAL: Duration = Duration.ofDays(1)

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<HousekeepingWorker>(INTERVAL.toMillis(), TimeUnit.MILLISECONDS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
