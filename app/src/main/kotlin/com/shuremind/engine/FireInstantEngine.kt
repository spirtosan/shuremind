package com.shuremind.engine

import java.time.Duration
import java.time.LocalTime
import java.time.Period
import java.time.ZoneId
import java.time.ZonedDateTime

/** Engine-facing snapshot of quiet hours + defaults needed to compute fire instants (subset of AppSettings). */
data class FireSettings(
    val quietHoursStart: LocalTime,
    val quietHoursEnd: LocalTime,
    val defaultAllDayTime: LocalTime
)

/** Everything FireInstantEngine needs about one task; Room-free bridge (like TaskSchedule). */
data class TaskFireInput(
    val taskId: String,
    val schedule: TaskSchedule,
    val createdAt: ZonedDateTime,
    val lastDoneAt: ZonedDateTime? = null,
    val snoozedUntil: ZonedDateTime? = null,
    /** ReminderRule.offsetIso values for this task (EVENT/ANNIVERSARY/DEADLINE only; ignored otherwise). */
    val reminderOffsets: List<String> = emptyList()
)

/**
 * Top-level M3 scheduling engine: computes the next pending fire instant for a task (STEP 1 of
 * CLAUDE.md's M3 prompt), combining the task's own next occurrence (OccurrenceEngine), lead-time
 * ReminderRule offsets, DEADLINE escalation slots, snooze suppression, and quiet-hours deferral.
 *
 * Every source function used here (OccurrenceEngine.nextOccurrence, NagEngine, CalendarRecurrenceEngine,
 * DeadlineEscalationEngine) already returns "the next matching instant at/after now" — i.e. is cursor-based.
 * [computeNextFire] preserves that property: calling it again with `now` = its own previous result (or
 * anything at/after it) always advances. This lets [missedSince] reuse it as a generic stepping oracle
 * instead of re-deriving type-specific occurrence-enumeration logic.
 */
object FireInstantEngine {

    enum class FireReason { OCCURRENCE, REMINDER_LEAD, ESCALATION, SNOOZE }

    /** [occurrenceAnchor] is the underlying due/occurrence instant this fire refers to (for notification identity/grouping). */
    data class FireInstant(val at: ZonedDateTime, val reason: FireReason, val occurrenceAnchor: ZonedDateTime)

    data class GlobalFire(val taskId: String, val occurrenceLocal: String, val reason: FireReason, val at: ZonedDateTime)

    /** Only these types get lead-time ReminderRule offsets (DATA_MODEL.md: "RECURRING/NAG fire at occurrence time"). */
    val LEAD_REMINDER_TYPES = setOf(TaskType.EVENT, TaskType.ANNIVERSARY, TaskType.DEADLINE)

    /** Safety bound for [missedSince]'s stepping loop (pathological configs, e.g. a 5-minute NAG left unattended for weeks). */
    private const val MAX_MISSED_STEPS = 2000

    fun computeNextFire(input: TaskFireInput, zone: ZoneId, now: ZonedDateTime, settings: FireSettings): FireInstant? {
        val schedule = input.schedule
        if (schedule.type == TaskType.SOMEDAY) return null

        // f) snoozed_until: suppress everything before it, fire AT it. A past snoozed_until is inert
        // (isAfter is strict, so once `now` reaches it, this branch stops applying with no extra state).
        val effectiveSnooze = input.snoozedUntil?.takeIf { it.isAfter(now) }
        if (effectiveSnooze != null) {
            val deferred = QuietHours.deferIfInside(effectiveSnooze, settings.quietHoursStart, settings.quietHoursEnd, zone)
            return FireInstant(deferred, FireReason.SNOOZE, effectiveSnooze)
        }

        val occurrence = OccurrenceEngine.nextOccurrence(schedule, zone, now, input.createdAt, input.lastDoneAt)
            ?: return null

        val candidates = mutableListOf(FireInstant(occurrence, FireReason.OCCURRENCE, occurrence))

        if (schedule.type in LEAD_REMINDER_TYPES) {
            for (offsetIso in input.reminderOffsets) {
                val lead = runCatching { applyOffsetBeforeDue(occurrence, offsetIso, zone) }.getOrNull()
                if (lead != null) candidates += FireInstant(lead, FireReason.REMINDER_LEAD, occurrence)
            }
        }

        if (schedule.type == TaskType.DEADLINE) {
            val slot = DeadlineEscalationEngine.nextSlot(occurrence, now, zone, settings.defaultAllDayTime, settings.quietHoursEnd)
            if (slot != null) candidates += FireInstant(slot, FireReason.ESCALATION, occurrence)
        }

        return candidates
            .map { it.copy(at = QuietHours.deferIfInside(it.at, settings.quietHoursStart, settings.quietHoursEnd, zone)) }
            .filter { !it.at.isBefore(now) }
            .minByOrNull { it.at }
    }

    /** Minimum fire instant across all tasks, plus every (task, occurrence, reason) tuple due at exactly that instant. */
    fun globalNext(inputs: List<TaskFireInput>, zone: ZoneId, now: ZonedDateTime, settings: FireSettings): Pair<ZonedDateTime, List<GlobalFire>>? {
        val perTask = inputs.mapNotNull { input -> computeNextFire(input, zone, now, settings)?.let { input.taskId to it } }
        val globalAt = perTask.minOfOrNull { it.second.at } ?: return null
        val due = perTask.filter { it.second.at == globalAt }.map { (taskId, fire) ->
            GlobalFire(taskId, OccurrenceLocalFormat.format(fire.occurrenceAnchor), fire.reason, fire.at)
        }
        return globalAt to due
    }

    /**
     * All fire instants in (watermark, now] — the recovery input for the overdue summary (D-23). Walks
     * each task's fire stream forward from the watermark via [computeNextFire], collecting every instant
     * that lands at/before [now], bounded by [MAX_MISSED_STEPS] per task against pathological configs.
     */
    fun missedSince(inputs: List<TaskFireInput>, zone: ZoneId, watermark: ZonedDateTime, now: ZonedDateTime, settings: FireSettings): List<GlobalFire> {
        if (!watermark.isBefore(now)) return emptyList()
        val missed = mutableListOf<GlobalFire>()
        for (input in inputs) {
            var cursor = watermark
            var steps = 0
            while (steps < MAX_MISSED_STEPS) {
                val fire = computeNextFire(input, zone, cursor, settings) ?: break
                if (fire.at.isAfter(now)) break
                missed += GlobalFire(input.taskId, OccurrenceLocalFormat.format(fire.occurrenceAnchor), fire.reason, fire.at)
                cursor = fire.at.plusSeconds(1)
                steps++
            }
        }
        return missed
    }

    /**
     * Lead-time offset before a due instant (DATA_MODEL.md ReminderRule.offset_iso: 'P14D','P1D','PT2H','PT0S').
     * Date-only offsets ('PnD') subtract whole calendar days from the due date (DST-safe per D-06, keeping
     * the same wall-clock time); offsets with a time component subtract a real Duration from the due instant.
     */
    private fun applyOffsetBeforeDue(dueInstant: ZonedDateTime, offsetIso: String, zone: ZoneId): ZonedDateTime =
        if (offsetIso.contains('T')) {
            dueInstant.minus(Duration.parse(offsetIso))
        } else {
            FixedDateEngine.toZonedDateTime(dueInstant.toLocalDate().minus(Period.parse(offsetIso)), dueInstant.toLocalTime(), zone)
        }
}
