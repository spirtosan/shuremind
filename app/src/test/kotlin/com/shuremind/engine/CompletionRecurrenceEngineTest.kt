package com.shuremind.engine

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class CompletionRecurrenceEngineTest {

    private val zone: ZoneId = ZoneId.of("Europe/Sofia")

    // --- Acceptance test #8: flowers every 3 days, COMPLETION anchor ---

    @Test
    fun `daily interval adds N days to the last completion`() {
        val lastDone = ZonedDateTime.of(2026, 6, 1, 9, 0, 0, 0, zone)
        val result = CompletionRecurrenceEngine.nextOccurrence(
            freq = RecurrenceFrequency.DAILY,
            interval = 3,
            timesOfDay = emptyList(),
            lastAnchor = lastDone,
            zone = zone
        )
        assertEquals(ZonedDateTime.of(2026, 6, 4, 9, 0, 0, 0, zone), result)
    }

    @Test
    fun `weekly interval adds N weeks to the last completion`() {
        val lastDone = ZonedDateTime.of(2026, 6, 1, 9, 0, 0, 0, zone)
        val result = CompletionRecurrenceEngine.nextOccurrence(
            freq = RecurrenceFrequency.WEEKLY,
            interval = 2,
            timesOfDay = emptyList(),
            lastAnchor = lastDone,
            zone = zone
        )
        assertEquals(LocalDate.of(2026, 6, 15), result.toLocalDate())
    }

    // --- Acceptance test #3: car oil, 12-month COMPLETION interval, month-end clamping ---

    @Test
    fun `monthly interval clamps month-end via java-time plusMonths`() {
        val lastDone = ZonedDateTime.of(2026, 1, 31, 9, 0, 0, 0, zone)
        val result = CompletionRecurrenceEngine.nextOccurrence(
            freq = RecurrenceFrequency.MONTHLY,
            interval = 1,
            timesOfDay = emptyList(),
            lastAnchor = lastDone,
            zone = zone
        )
        assertEquals(LocalDate.of(2026, 2, 28), result.toLocalDate())
    }

    @Test
    fun `yearly 12-month-equivalent interval from car oil example`() {
        val lastDone = ZonedDateTime.of(2025, 3, 10, 9, 0, 0, 0, zone)
        val result = CompletionRecurrenceEngine.nextOccurrence(
            freq = RecurrenceFrequency.MONTHLY,
            interval = 12,
            timesOfDay = emptyList(),
            lastAnchor = lastDone,
            zone = zone
        )
        assertEquals(LocalDate.of(2026, 3, 10), result.toLocalDate())
    }

    @Test
    fun `uses the explicit time-of-day when provided, otherwise the anchor's time`() {
        val lastDone = ZonedDateTime.of(2026, 6, 1, 14, 45, 0, 0, zone)
        val withExplicitTime = CompletionRecurrenceEngine.nextOccurrence(
            freq = RecurrenceFrequency.DAILY,
            interval = 1,
            timesOfDay = listOf(LocalTime.of(8, 0)),
            lastAnchor = lastDone,
            zone = zone
        )
        assertEquals(LocalTime.of(8, 0), withExplicitTime.toLocalTime())

        val withoutExplicitTime = CompletionRecurrenceEngine.nextOccurrence(
            freq = RecurrenceFrequency.DAILY,
            interval = 1,
            timesOfDay = emptyList(),
            lastAnchor = lastDone,
            zone = zone
        )
        assertEquals(LocalTime.of(14, 45), withoutExplicitTime.toLocalTime())
    }

    // --- Falls back to created_at when never done (via OccurrenceEngine dispatch) ---

    @Test
    fun `completion anchor falls back to created_at when never done`() {
        val schedule = TaskSchedule(
            type = TaskType.RECURRING,
            recFreq = RecurrenceFrequency.DAILY,
            recInterval = 3,
            recAnchor = RecurrenceAnchor.COMPLETION
        )
        val createdAt = ZonedDateTime.of(2026, 6, 1, 9, 0, 0, 0, zone)
        val now = ZonedDateTime.of(2026, 6, 2, 0, 0, 0, 0, zone)
        val result = OccurrenceEngine.nextOccurrence(schedule, zone, now, createdAt, lastDoneAt = null)
        assertEquals(LocalDate.of(2026, 6, 4), result?.toLocalDate())
    }
}
