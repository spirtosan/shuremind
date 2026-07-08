package com.shuremind.system

import com.shuremind.data.entity.CompletionLogEntity
import com.shuremind.data.entity.TaskEntity
import com.shuremind.data.repo.CompletionRepository
import com.shuremind.data.repo.DeliveryWatermarkRepository
import com.shuremind.data.repo.NextFireAtCalculator
import com.shuremind.data.repo.ReminderRuleRepository
import com.shuremind.data.repo.SettingsRepository
import com.shuremind.data.repo.TaskRepository
import com.shuremind.data.repo.toFireInput
import com.shuremind.data.repo.toFireSettings
import com.shuremind.data.repo.toSchedule
import com.shuremind.engine.CalendarRecurrenceEngine
import com.shuremind.engine.CompletionAction
import com.shuremind.engine.FireInstantEngine
import com.shuremind.engine.OccurrenceLocalFormat
import com.shuremind.engine.RecurrenceAnchor
import com.shuremind.engine.TaskFireInput
import com.shuremind.engine.TaskStatus
import com.shuremind.engine.TaskType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The single M3 entry point (D-07/D-10/D-23), called from: alarm fire, boot receiver, MainActivity
 * onStart, the housekeeping worker, and after every repository write that can change scheduling.
 *
 * On each run: (1) auto-skips missed CALENDAR-grid occurrences and recomputes every active task's
 * next_fire_at cache, (2) checks the delivered watermark for anything missed while the device was
 * asleep/off and posts a loud overdue summary if so (then advances the watermark), (3) re-arms the
 * single next AlarmManager alarm (or cancels it if nothing is scheduled).
 */
class RecomputeAndRearm(
    private val taskRepository: TaskRepository,
    private val completionRepository: CompletionRepository,
    private val reminderRuleRepository: ReminderRuleRepository,
    private val settingsRepository: SettingsRepository,
    private val deliveryWatermarkRepository: DeliveryWatermarkRepository,
    private val alarmArmer: AlarmArmer,
    private val overdueSummaryNotifier: OverdueSummaryNotifier,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val nowProvider: () -> ZonedDateTime = { ZonedDateTime.now(zone) }
) {
    // run() itself writes to taskRepository/completionRepository (recompute/auto-skip), and those
    // repositories call back into run() via ScheduleChangeNotifier — without a guard that would
    // recurse. tryLock (non-blocking, non-reentrant) is that guard, but a failed tryLock must NOT
    // just drop the call: a genuinely concurrent caller (e.g. a task created mid-pass) may have
    // changed state the in-flight pass already read past. Instead it flags [rerunRequested] so the
    // current lock holder loops and does another full pass — with a fresh `now` — once its current
    // one finishes. Nested calls from run()'s own writes flag a rerun too, but that extra pass is a
    // no-op once state has converged (every write below is conditional on something having actually
    // changed), so the loop always terminates.
    private val runLock = Mutex()
    private val rerunRequested = AtomicBoolean(false)

    suspend fun run(now: ZonedDateTime = ZonedDateTime.now(zone)) {
        if (!runLock.tryLock()) {
            rerunRequested.set(true)
            return
        }
        try {
            var passNow = now
            do {
                rerunRequested.set(false)
                runLocked(passNow)
                passNow = nowProvider()
            } while (rerunRequested.get())
        } finally {
            runLock.unlock()
        }
    }

    private suspend fun runLocked(now: ZonedDateTime) {
        val tasks = activeTasks()
        val settings = settingsRepository.settings.first()
        val fireSettings = settings.toFireSettings()

        val inputs = tasks.map { task ->
            autoSkipMissedCalendarOccurrences(task, now)
            val lastDoneAt = lastDoneAtFor(task)
            recomputeNextFireAt(task, lastDoneAt, now)
            task.toFireInput(zone, reminderOffsetsFor(task), lastDoneAt)
        }

        val watermark = Instant.ofEpochMilli(deliveryWatermarkRepository.get()).atZone(zone)
        if (watermark.isBefore(now)) {
            val missed = FireInstantEngine.missedSince(inputs, zone, watermark, now, fireSettings)
            if (missed.isNotEmpty()) {
                overdueSummaryNotifier.postOverdueSummary(missed.size)
            }
            deliveryWatermarkRepository.advanceTo(now.toInstant().toEpochMilli())
        }

        val globalNext = FireInstantEngine.globalNext(inputs, zone, now, fireSettings)
        if (globalNext != null) {
            val isAlarm = globalNext.second.any { it.isAlarm }
            alarmArmer.arm(globalNext.first.toInstant().toEpochMilli(), settings.exactAlarmsOptIn, isAlarm)
        } else {
            alarmArmer.cancel()
        }
    }

    /**
     * Read-only pre-step for AlarmReceiver (STEP 4): everything due in (watermark, now], paired with
     * its TaskEntity for notification building. Does NOT touch the watermark or the alarm — callers
     * that post individual notifications for this batch must advance the watermark themselves
     * afterward, then call [run] for the rest (recompute/auto-skip/re-arm). This keeps the normal
     * on-time per-occurrence notifications distinct from the [overdueSummaryNotifier]'s grouped
     * catch-up summary: by the time [run] re-reads the (now just-advanced) watermark, this batch is
     * no longer "missed", so it isn't posted twice.
     */
    suspend fun collectDueSinceWatermark(now: ZonedDateTime = ZonedDateTime.now(zone)): List<Pair<TaskEntity, FireInstantEngine.GlobalFire>> {
        val watermark = Instant.ofEpochMilli(deliveryWatermarkRepository.get()).atZone(zone)
        if (!watermark.isBefore(now)) return emptyList()

        val tasks = activeTasks()
        val fireSettings = settingsRepository.settings.first().toFireSettings()
        val inputs = tasks.map { buildFireInput(it) }
        val missed = FireInstantEngine.missedSince(inputs, zone, watermark, now, fireSettings)

        val tasksById = tasks.associateBy { it.id }
        return missed.mapNotNull { fire -> tasksById[fire.taskId]?.let { it to fire } }
    }

    // Only ever-active (not yet completed, not archived, not soft-deleted) tasks participate in
    // scheduling; observeActive()'s default excludes ARCHIVED only, so DONE one-shots must be
    // filtered out here too.
    private suspend fun activeTasks(): List<TaskEntity> =
        taskRepository.observeActive().first().filter { it.status == TaskStatus.ACTIVE }

    private suspend fun buildFireInput(task: TaskEntity): TaskFireInput {
        val lastDoneAt = lastDoneAtFor(task)
        return task.toFireInput(zone, reminderOffsetsFor(task), lastDoneAt)
    }

    private suspend fun lastDoneAtFor(task: TaskEntity): ZonedDateTime? =
        if (task.recAnchor == RecurrenceAnchor.COMPLETION) {
            completionRepository.getLastByAction(task.id, CompletionAction.DONE)?.completedAt
                ?.let { Instant.ofEpochMilli(it).atZone(zone) }
        } else {
            null
        }

    private suspend fun reminderOffsetsFor(task: TaskEntity): List<String> =
        if (task.type in FireInstantEngine.LEAD_REMINDER_TYPES) {
            reminderRuleRepository.getForTask(task.id).map { it.offsetIso }
        } else {
            emptyList()
        }

    private suspend fun recomputeNextFireAt(task: TaskEntity, lastDoneAt: ZonedDateTime?, now: ZonedDateTime) {
        val newValue = if (task.type == TaskType.SOMEDAY) null else NextFireAtCalculator.compute(task, zone, now, lastDoneAt)
        if (newValue != task.nextFireAt) {
            taskRepository.update(task.copy(nextFireAt = newValue))
        }
    }

    /**
     * DATA_MODEL.md "Recurrence semantics": CALENDAR-anchor occurrences don't pile up — only WINDOW
     * (always CALENDAR-grid) and RECURRING(CALENDAR) tasks are affected; COMPLETION-anchor recurrence
     * has no independent grid to miss. Uses the task's *stale* (pre-recompute) next_fire_at as the
     * "since" boundary, so this must run before [recomputeNextFireAt] overwrites it.
     */
    private suspend fun autoSkipMissedCalendarOccurrences(task: TaskEntity, now: ZonedDateTime) {
        val isCalendarGrid = task.type == TaskType.WINDOW ||
            (task.type == TaskType.RECURRING && task.recAnchor == RecurrenceAnchor.CALENDAR)
        if (!isCalendarGrid) return

        val schedule = task.toSchedule()
        val freq = schedule.recFreq ?: return
        val since = task.nextFireAt?.let { Instant.ofEpochMilli(it).atZone(zone) } ?: return
        if (!since.isBefore(now)) return

        val occurrences = CalendarRecurrenceEngine.occurrencesBetween(
            freq = freq,
            interval = schedule.recInterval,
            daysOfWeek = schedule.recDaysOfWeek,
            dayOfMonth = schedule.recDayOfMonth,
            timesOfDay = schedule.recTimesOfDay,
            endDate = schedule.recEndDate,
            anchorDate = schedule.dueLocalDate ?: Instant.ofEpochMilli(task.createdAt).atZone(zone).toLocalDate(),
            zone = zone,
            since = since,
            now = now
        )
        val resolution = CalendarRecurrenceEngine.resolveMissed(occurrences)
        for (occurrence in resolution.toAutoSkip) {
            completionRepository.recordCompletion(
                CompletionLogEntity(
                    id = UUID.randomUUID().toString(),
                    taskId = task.id,
                    occurrenceLocal = OccurrenceLocalFormat.format(occurrence),
                    action = CompletionAction.SKIPPED,
                    completedAt = now.toInstant().toEpochMilli(),
                    meterValue = null,
                    note = null
                )
            )
        }
    }
}
