package com.shuremind.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class CalendarRecurrenceEngineTest {

    private val zone: ZoneId = ZoneId.of("Europe/Sofia")

    // --- Acceptance test #6: meds intake, daily 08:00/20:00 ---

    @Test
    fun `daily multi-dose picks the next time slot on the same day`() {
        val anchor = LocalDate.of(2026, 1, 1)
        val now = ZonedDateTime.of(2026, 6, 1, 10, 0, 0, 0, zone) // between 08:00 and 20:00
        val result = CalendarRecurrenceEngine.nextOccurrence(
            freq = RecurrenceFrequency.DAILY,
            interval = 1,
            daysOfWeek = emptySet(),
            dayOfMonth = null,
            timesOfDay = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)),
            endDate = null,
            anchorDate = anchor,
            zone = zone,
            now = now
        )
        assertEquals(ZonedDateTime.of(2026, 6, 1, 20, 0, 0, 0, zone), result)
    }

    @Test
    fun `daily multi-dose rolls to the next day after the last slot`() {
        val anchor = LocalDate.of(2026, 1, 1)
        val now = ZonedDateTime.of(2026, 6, 1, 21, 0, 0, 0, zone) // after 20:00
        val result = CalendarRecurrenceEngine.nextOccurrence(
            freq = RecurrenceFrequency.DAILY,
            interval = 1,
            daysOfWeek = emptySet(),
            dayOfMonth = null,
            timesOfDay = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)),
            endDate = null,
            anchorDate = anchor,
            zone = zone,
            now = now
        )
        assertEquals(ZonedDateTime.of(2026, 6, 2, 8, 0, 0, 0, zone), result)
    }

    @Test
    fun `daily interval 2 only matches every other day from the anchor`() {
        val anchor = LocalDate.of(2026, 6, 1)
        val now = ZonedDateTime.of(2026, 6, 2, 0, 0, 0, 0, zone) // one day after anchor
        val result = CalendarRecurrenceEngine.nextOccurrence(
            freq = RecurrenceFrequency.DAILY,
            interval = 2,
            daysOfWeek = emptySet(),
            dayOfMonth = null,
            timesOfDay = listOf(LocalTime.of(9, 0)),
            endDate = null,
            anchorDate = anchor,
            zone = zone,
            now = now
        )
        // June 2 doesn't match (odd offset from anchor), so next hit is June 3
        assertEquals(LocalDate.of(2026, 6, 3), result?.toLocalDate())
    }

    // --- WEEKLY ---

    @Test
    fun `weekly with specific days of week lands on the next matching weekday`() {
        val anchor = LocalDate.of(2026, 6, 1) // a Monday
        val now = ZonedDateTime.of(2026, 6, 2, 0, 0, 0, 0, zone) // Tuesday
        val result = CalendarRecurrenceEngine.nextOccurrence(
            freq = RecurrenceFrequency.WEEKLY,
            interval = 1,
            daysOfWeek = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
            dayOfMonth = null,
            timesOfDay = listOf(LocalTime.of(9, 0)),
            endDate = null,
            anchorDate = anchor,
            zone = zone,
            now = now
        )
        assertEquals(DayOfWeek.WEDNESDAY, result?.dayOfWeek)
        assertEquals(LocalDate.of(2026, 6, 3), result?.toLocalDate())
    }

    @Test
    fun `weekly interval 2 skips alternate weeks`() {
        val anchor = LocalDate.of(2026, 6, 1) // Monday, week 0
        // June 8 is Monday of the very next week (week 1) - should NOT match with interval 2
        val now = ZonedDateTime.of(2026, 6, 8, 0, 0, 0, 0, zone)
        val result = CalendarRecurrenceEngine.nextOccurrence(
            freq = RecurrenceFrequency.WEEKLY,
            interval = 2,
            daysOfWeek = setOf(DayOfWeek.MONDAY),
            dayOfMonth = null,
            timesOfDay = listOf(LocalTime.of(9, 0)),
            endDate = null,
            anchorDate = anchor,
            zone = zone,
            now = now
        )
        // Week 1 (June 8) is skipped; next matching week (week 2) is June 15
        assertEquals(LocalDate.of(2026, 6, 15), result?.toLocalDate())
    }

    // --- MONTHLY: month-end clamping (explicit CLAUDE.md requirement) ---

    @Test
    fun `monthly day-of-month 31 clamps to April 30`() {
        val anchor = LocalDate.of(2026, 1, 31)
        val now = ZonedDateTime.of(2026, 4, 1, 0, 0, 0, 0, zone)
        val result = CalendarRecurrenceEngine.nextOccurrence(
            freq = RecurrenceFrequency.MONTHLY,
            interval = 1,
            daysOfWeek = emptySet(),
            dayOfMonth = 31,
            timesOfDay = listOf(LocalTime.of(9, 0)),
            endDate = null,
            anchorDate = anchor,
            zone = zone,
            now = now
        )
        assertEquals(LocalDate.of(2026, 4, 30), result?.toLocalDate())
    }

    @Test
    fun `monthly day-of-month 31 clamps to Feb 28 in a non-leap year`() {
        val anchor = LocalDate.of(2026, 1, 31)
        val now = ZonedDateTime.of(2026, 2, 1, 0, 0, 0, 0, zone)
        val result = CalendarRecurrenceEngine.nextOccurrence(
            freq = RecurrenceFrequency.MONTHLY,
            interval = 1,
            daysOfWeek = emptySet(),
            dayOfMonth = 31,
            timesOfDay = listOf(LocalTime.of(9, 0)),
            endDate = null,
            anchorDate = anchor,
            zone = zone,
            now = now
        )
        assertEquals(LocalDate.of(2026, 2, 28), result?.toLocalDate())
    }

    // --- YEARLY (grid variant, mirrors ANNIVERSARY's clamping) ---

    @Test
    fun `yearly clamps Feb 29 anchor to Feb 28 in a non-leap target year`() {
        val anchor = LocalDate.of(2024, 2, 29) // a leap year anchor
        val now = ZonedDateTime.of(2026, 1, 1, 0, 0, 0, 0, zone)
        val result = CalendarRecurrenceEngine.nextOccurrence(
            freq = RecurrenceFrequency.YEARLY,
            interval = 1,
            daysOfWeek = emptySet(),
            dayOfMonth = null,
            timesOfDay = listOf(LocalTime.of(9, 0)),
            endDate = null,
            anchorDate = anchor,
            zone = zone,
            now = now
        )
        assertEquals(LocalDate.of(2026, 2, 28), result?.toLocalDate())
    }

    // --- rec_end_date and not_before ---

    @Test
    fun `recurrence stops once past the end date`() {
        val anchor = LocalDate.of(2026, 1, 1)
        val now = ZonedDateTime.of(2026, 6, 2, 0, 0, 0, 0, zone)
        val result = CalendarRecurrenceEngine.nextOccurrence(
            freq = RecurrenceFrequency.DAILY,
            interval = 1,
            daysOfWeek = emptySet(),
            dayOfMonth = null,
            timesOfDay = listOf(LocalTime.of(9, 0)),
            endDate = LocalDate.of(2026, 6, 1),
            anchorDate = anchor,
            zone = zone,
            now = now
        )
        assertNull(result)
    }

    @Test
    fun `not_before pushes the search start forward even if the grid would match earlier`() {
        val anchor = LocalDate.of(2026, 1, 1)
        val now = ZonedDateTime.of(2026, 6, 1, 0, 0, 0, 0, zone)
        val result = CalendarRecurrenceEngine.nextOccurrence(
            freq = RecurrenceFrequency.DAILY,
            interval = 1,
            daysOfWeek = emptySet(),
            dayOfMonth = null,
            timesOfDay = listOf(LocalTime.of(9, 0)),
            endDate = null,
            anchorDate = anchor,
            zone = zone,
            now = now,
            notBefore = LocalDate.of(2026, 9, 1)
        )
        assertEquals(LocalDate.of(2026, 9, 1), result?.toLocalDate())
    }

    // --- WINDOW dispatch (acceptance test #5: monthly check from September) ---

    @Test
    fun `WINDOW behaves as a monthly check cadence via the occurrence engine`() {
        val schedule = TaskSchedule(
            type = TaskType.WINDOW,
            dueLocalDate = LocalDate.of(2026, 9, 1),
            recFreq = RecurrenceFrequency.MONTHLY,
            recInterval = 1,
            recDayOfMonth = 1,
            recTimesOfDay = listOf(LocalTime.of(9, 0))
        )
        val now = ZonedDateTime.of(2026, 9, 15, 0, 0, 0, 0, zone)
        val createdAt = ZonedDateTime.of(2026, 8, 1, 0, 0, 0, 0, zone)
        val result = OccurrenceEngine.nextOccurrence(schedule, zone, now, createdAt)
        assertEquals(LocalDate.of(2026, 10, 1), result?.toLocalDate())
    }
}
