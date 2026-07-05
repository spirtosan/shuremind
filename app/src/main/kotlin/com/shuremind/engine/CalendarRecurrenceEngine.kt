package com.shuremind.engine

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/**
 * CALENDAR-anchor recurrence: next occurrence comes from a fixed grid (freq/interval/days/times),
 * independent of completion. Used by RECURRING(CALENDAR) and WINDOW's check cadence.
 */
object CalendarRecurrenceEngine {

    // Safety bound so a pathological config (e.g. a day-of-week that never matches an interval)
    // can't spin forever; ~2 years covers every realistic v1 cadence (including YEARLY).
    private const val MAX_SEARCH_DAYS = 800L

    private fun weekStart(date: LocalDate): LocalDate =
        date.minusDays((date.dayOfWeek.value - DayOfWeek.MONDAY.value).toLong())

    private fun matchesGrid(
        freq: RecurrenceFrequency,
        interval: Int,
        daysOfWeek: Set<DayOfWeek>,
        dayOfMonth: Int?,
        anchorDate: LocalDate,
        candidateDate: LocalDate
    ): Boolean = when (freq) {
        RecurrenceFrequency.DAILY ->
            ChronoUnit.DAYS.between(anchorDate, candidateDate) % interval == 0L

        RecurrenceFrequency.WEEKLY -> {
            val weeksBetween = ChronoUnit.WEEKS.between(weekStart(anchorDate), weekStart(candidateDate))
            val dayOk = if (daysOfWeek.isNotEmpty()) {
                candidateDate.dayOfWeek in daysOfWeek
            } else {
                candidateDate.dayOfWeek == anchorDate.dayOfWeek
            }
            weeksBetween % interval == 0L && dayOk
        }

        RecurrenceFrequency.MONTHLY -> {
            val monthsBetween = ChronoUnit.MONTHS.between(
                anchorDate.withDayOfMonth(1),
                candidateDate.withDayOfMonth(1)
            )
            val targetDom = (dayOfMonth ?: anchorDate.dayOfMonth).coerceAtMost(candidateDate.lengthOfMonth())
            monthsBetween % interval == 0L && candidateDate.dayOfMonth == targetDom
        }

        RecurrenceFrequency.YEARLY -> {
            val yearsBetween = (candidateDate.year - anchorDate.year).toLong()
            val targetDate = FixedDateEngine.clampedDate(candidateDate.year, anchorDate.monthValue, anchorDate.dayOfMonth)
            yearsBetween % interval == 0L && candidateDate == targetDate
        }
    }

    /** Lazily yields every grid occurrence (date+time) from [searchStartDate] onward, respecting rec_end_date. */
    fun occurrenceSequence(
        freq: RecurrenceFrequency,
        interval: Int,
        daysOfWeek: Set<DayOfWeek>,
        dayOfMonth: Int?,
        timesOfDay: List<LocalTime>,
        endDate: LocalDate?,
        anchorDate: LocalDate,
        zone: ZoneId,
        searchStartDate: LocalDate
    ): Sequence<ZonedDateTime> = sequence {
        val times = timesOfDay.ifEmpty { listOf(EngineTuning.DEFAULT_ALL_DAY_TIME) }.sorted()
        val effectiveInterval = interval.coerceAtLeast(1)
        var date = maxOf(searchStartDate, anchorDate)
        var daysScanned = 0L
        while (daysScanned <= MAX_SEARCH_DAYS) {
            if (endDate != null && date.isAfter(endDate)) return@sequence
            if (matchesGrid(freq, effectiveInterval, daysOfWeek, dayOfMonth, anchorDate, date)) {
                for (t in times) {
                    yield(LocalDateTime.of(date, t).atZone(zone))
                }
            }
            date = date.plusDays(1)
            daysScanned++
        }
    }

    /** Next CALENDAR-anchor occurrence at or after [now]. */
    fun nextOccurrence(
        freq: RecurrenceFrequency,
        interval: Int,
        daysOfWeek: Set<DayOfWeek>,
        dayOfMonth: Int?,
        timesOfDay: List<LocalTime>,
        endDate: LocalDate?,
        anchorDate: LocalDate,
        zone: ZoneId,
        now: ZonedDateTime,
        notBefore: LocalDate? = null
    ): ZonedDateTime? {
        val searchStart = maxOf(now.toLocalDate(), notBefore ?: now.toLocalDate())
        return occurrenceSequence(freq, interval, daysOfWeek, dayOfMonth, timesOfDay, endDate, anchorDate, zone, searchStart)
            .firstOrNull { !it.isBefore(now) }
    }

    /** All grid occurrences in [[since], [now]) — candidates for housekeeping auto-skip. */
    fun occurrencesBetween(
        freq: RecurrenceFrequency,
        interval: Int,
        daysOfWeek: Set<DayOfWeek>,
        dayOfMonth: Int?,
        timesOfDay: List<LocalTime>,
        endDate: LocalDate?,
        anchorDate: LocalDate,
        zone: ZoneId,
        since: ZonedDateTime,
        now: ZonedDateTime
    ): List<ZonedDateTime> =
        occurrenceSequence(freq, interval, daysOfWeek, dayOfMonth, timesOfDay, endDate, anchorDate, zone, since.toLocalDate())
            .takeWhile { it.isBefore(now) }
            .filter { !it.isBefore(since) }
            .toList()

    /** Result of resolving a run of missed CALENDAR occurrences at housekeeping time. */
    data class MissedResolution(val toAutoSkip: List<ZonedDateTime>, val current: ZonedDateTime?)

    /**
     * Per DATA_MODEL.md: "Missed occurrences don't pile up: completing marks the *current* one;
     * older ones auto-log as SKIPPED at housekeeping." The latest past occurrence stays actionable;
     * everything earlier is skipped.
     */
    fun resolveMissed(occurrences: List<ZonedDateTime>): MissedResolution =
        if (occurrences.isEmpty()) {
            MissedResolution(emptyList(), null)
        } else {
            MissedResolution(occurrences.dropLast(1), occurrences.last())
        }
}
