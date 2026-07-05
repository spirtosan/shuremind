package com.shuremind.engine

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** COMPLETION-anchor recurrence: next = last DONE completion + interval (falls back to created_at if never done). */
object CompletionRecurrenceEngine {

    fun nextOccurrence(
        freq: RecurrenceFrequency,
        interval: Int,
        timesOfDay: List<LocalTime>,
        lastAnchor: ZonedDateTime,
        zone: ZoneId
    ): ZonedDateTime {
        val effectiveInterval = interval.coerceAtLeast(1).toLong()
        val anchorDate = lastAnchor.toLocalDate()
        // java.time's plusMonths/plusYears already clamp an overflowing day-of-month to the
        // target month's length (e.g. Jan 31 + 1 month -> Feb 28), matching month-end clamping.
        val nextDate: LocalDate = when (freq) {
            RecurrenceFrequency.DAILY -> anchorDate.plusDays(effectiveInterval)
            RecurrenceFrequency.WEEKLY -> anchorDate.plusWeeks(effectiveInterval)
            RecurrenceFrequency.MONTHLY -> anchorDate.plusMonths(effectiveInterval)
            RecurrenceFrequency.YEARLY -> anchorDate.plusYears(effectiveInterval)
        }
        val time = timesOfDay.firstOrNull() ?: lastAnchor.toLocalTime()
        return LocalDateTime.of(nextDate, time).atZone(zone)
    }
}
