package com.shuremind

import android.app.Application
import com.shuremind.data.entity.TaskEntity
import com.shuremind.data.repo.NextFireAtCalculator
import com.shuremind.data.repo.toCsv
import com.shuremind.di.AppContainer
import com.shuremind.engine.RecurrenceAnchor
import com.shuremind.engine.RecurrenceFrequency
import com.shuremind.engine.TaskStatus
import com.shuremind.engine.TaskType
import com.shuremind.system.HousekeepingWorker
import com.shuremind.system.NotificationChannels
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

class ShuRemindApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        NotificationChannels.ensureCreated(this)
        HousekeepingWorker.schedule(this)
        CoroutineScope(Dispatchers.Default).launch { seedWeeklyReviewTaskIfNeeded() }
    }

    /**
     * D-28: seeds the "Weekly review" reminder (RECURRING CALENDAR, weekly, Sunday 18:00) once, on
     * first run after this version. Idempotent via the stored seeded-task-id flag; the user may
     * freely delete or edit the resulting task afterward without it ever being reseeded.
     */
    private suspend fun seedWeeklyReviewTaskIfNeeded() {
        if (container.weeklyReviewSeedRepository.isSeeded()) return
        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        val entity = TaskEntity(
            id = id,
            title = getString(R.string.weekly_review_task_title),
            notes = null,
            type = TaskType.RECURRING,
            status = TaskStatus.ACTIVE,
            impact = 1,
            urgency = 1,
            estimatedCost = null,
            dueLocalDate = null,
            dueLocalTime = null,
            notBefore = null,
            recFreq = RecurrenceFrequency.WEEKLY,
            recInterval = 1,
            recAnchor = RecurrenceAnchor.CALENDAR,
            recDaysOfWeek = setOf(DayOfWeek.SUNDAY).toCsv(),
            recDayOfMonth = null,
            recTimesOfDay = listOf(LocalTime.of(18, 0)).toCsv(),
            recEndDate = null,
            nagIntervalHours = null,
            stockQty = null,
            dosePerIntake = null,
            restockLeadDays = null,
            stockRecordedAt = null,
            meterName = null,
            meterInterval = null,
            lastDoneMeter = null,
            windowHint = null,
            snoozedUntil = null,
            nextFireAt = null,
            createdAt = now,
            updatedAt = now,
            deletedAt = null,
            dirty = 1
        )
        val nowZdt = Instant.ofEpochMilli(now).atZone(zone)
        val withNextFire = entity.copy(nextFireAt = NextFireAtCalculator.compute(entity, zone, nowZdt, lastDoneAt = null))
        container.taskRepository.upsert(withNextFire, now)
        container.weeklyReviewSeedRepository.markSeeded(id)
    }
}
