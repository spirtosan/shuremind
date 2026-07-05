package com.shuremind.engine

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime

/** EVENT / DEADLINE (single fixed instant) and ANNIVERSARY (yearly) occurrence math. */
object FixedDateEngine {

    /** Clamps day-of-month to the last valid day of the given year/month (e.g. Feb 29 on a non-leap year -> Feb 28). */
    fun clampedDate(year: Int, month: Int, dayOfMonth: Int): LocalDate {
        val yearMonth = YearMonth.of(year, month)
        return yearMonth.atDay(dayOfMonth.coerceAtMost(yearMonth.lengthOfMonth()))
    }

    /**
     * Local wall-clock -> zoned instant. Per D-06, java.time's default gap/overlap resolution already
     * matches the required behavior: a nonexistent spring-forward time is shifted forward by the gap
     * length, and an ambiguous fall-back time resolves to the earlier offset (first occurrence).
     */
    fun toZonedDateTime(date: LocalDate, time: LocalTime, zone: ZoneId): ZonedDateTime =
        LocalDateTime.of(date, time).atZone(zone)

    /** EVENT / DEADLINE: a single fixed instant, no repetition. */
    fun fixedOccurrence(dueLocalDate: LocalDate?, dueLocalTime: LocalTime?, zone: ZoneId): ZonedDateTime? {
        val date = dueLocalDate ?: return null
        return toZonedDateTime(date, dueLocalTime ?: EngineTuning.DEFAULT_ALL_DAY_TIME, zone)
    }

    /** ANNIVERSARY: yearly on the same month/day (clamped for Feb 29), advancing the year until not in the past. */
    fun anniversaryOccurrence(
        dueLocalDate: LocalDate,
        dueLocalTime: LocalTime?,
        zone: ZoneId,
        now: ZonedDateTime
    ): ZonedDateTime {
        val time = dueLocalTime ?: EngineTuning.DEFAULT_ALL_DAY_TIME
        var year = now.toLocalDate().year
        var candidate = toZonedDateTime(clampedDate(year, dueLocalDate.monthValue, dueLocalDate.dayOfMonth), time, zone)
        if (candidate.isBefore(now)) {
            year += 1
            candidate = toZonedDateTime(clampedDate(year, dueLocalDate.monthValue, dueLocalDate.dayOfMonth), time, zone)
        }
        return candidate
    }
}
