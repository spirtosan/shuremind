package com.shuremind.engine

import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/**
 * DEADLINE-only escalation curve (DATA_MODEL.md "Deadline escalation", CLAUDE.md M3 spec): additional
 * fire slots layered on top of the plain due-instant as the deadline approaches, growing more frequent
 * the closer (or more overdue) the task is. Not applied to any other task type.
 *
 * Pre-due slots are bucketed by whole calendar days to due (DST-safe, matches D-06); once the due
 * instant itself has passed, slots become a fixed cadence anchored to the user's quiet-hours end so
 * they naturally interleave with (and get subsumed by) general quiet-hour deferral.
 */
object DeadlineEscalationEngine {

    private const val PRE_DUE_WINDOW_DAYS = 14L

    private fun slotsForBucket(daysToDue: Long, defaultAllDayTime: LocalTime): List<LocalTime> = when {
        daysToDue <= 2 -> EngineTuning.ESCALATION_TIMES_WITHIN_2D
        daysToDue <= 7 -> EngineTuning.ESCALATION_TIMES_WITHIN_7D
        daysToDue <= PRE_DUE_WINDOW_DAYS -> listOf(defaultAllDayTime)
        else -> emptyList()
    }

    /** Next escalation slot at/after [now], or null if none applies (task not yet in the escalation window). */
    fun nextSlot(
        dueInstant: ZonedDateTime,
        now: ZonedDateTime,
        zone: ZoneId,
        defaultAllDayTime: LocalTime,
        quietHoursEnd: LocalTime
    ): ZonedDateTime? {
        if (!now.isBefore(dueInstant)) {
            return nextOverdueSlot(dueInstant, now, zone, quietHoursEnd)
        }

        val dueDate = dueInstant.toLocalDate()
        if (ChronoUnit.DAYS.between(now.toLocalDate(), dueDate) > PRE_DUE_WINDOW_DAYS) return null

        var date = now.toLocalDate()
        while (!date.isAfter(dueDate)) {
            val daysToDue = ChronoUnit.DAYS.between(date, dueDate)
            val times = slotsForBucket(daysToDue, defaultAllDayTime).sorted()
            for (t in times) {
                val candidate = FixedDateEngine.toZonedDateTime(date, t, zone)
                if (!candidate.isBefore(now) && candidate.isBefore(dueInstant)) return candidate
            }
            date = date.plusDays(1)
        }
        return null
    }

    /** Every OVERDUE_ESCALATION_INTERVAL, phase-aligned to quiet-hours end so it interleaves with quiet-hour deferral. */
    private fun nextOverdueSlot(dueInstant: ZonedDateTime, now: ZonedDateTime, zone: ZoneId, quietHoursEnd: LocalTime): ZonedDateTime {
        val anchor: ZonedDateTime = LocalDateTime.of(dueInstant.toLocalDate(), quietHoursEnd).atZone(zone)
        return NagEngine.nextOccurrence(
            intervalHours = EngineTuning.OVERDUE_ESCALATION_INTERVAL.toHours().toDouble(),
            anchor = anchor,
            notBefore = null,
            zone = zone,
            now = now
        )
    }
}
