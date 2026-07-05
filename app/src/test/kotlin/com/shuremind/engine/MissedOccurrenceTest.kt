package com.shuremind.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Per DATA_MODEL.md: CALENDAR-anchor missed occurrences don't pile up — completing marks the
 * *current* (latest past) occurrence; older ones auto-log as SKIPPED at housekeeping.
 */
class MissedOccurrenceTest {

    private val zone: ZoneId = ZoneId.of("Europe/Sofia")

    @Test
    fun `several missed daily occurrences resolve to one current and the rest auto-skipped`() {
        val anchor = LocalDate.of(2026, 1, 1)
        val since = ZonedDateTime.of(2026, 6, 1, 8, 0, 0, 0, zone) // last known fire
        val now = ZonedDateTime.of(2026, 6, 4, 12, 0, 0, 0, zone) // housekeeping runs 3 days later

        val occurrences = CalendarRecurrenceEngine.occurrencesBetween(
            freq = RecurrenceFrequency.DAILY,
            interval = 1,
            daysOfWeek = emptySet(),
            dayOfMonth = null,
            timesOfDay = listOf(LocalTime.of(8, 0)),
            endDate = null,
            anchorDate = anchor,
            zone = zone,
            since = since,
            now = now
        )
        // June 1, 2, 3 (June 4 08:00 is before "now" 12:00 too, so it's included as well)
        assertEquals(4, occurrences.size)

        val resolution = CalendarRecurrenceEngine.resolveMissed(occurrences)
        assertEquals(3, resolution.toAutoSkip.size)
        assertEquals(LocalDate.of(2026, 6, 4), resolution.current?.toLocalDate())
        assertTrue(resolution.toAutoSkip.all { it.toLocalDate() < LocalDate.of(2026, 6, 4) })
    }

    @Test
    fun `no missed occurrences when housekeeping runs right on schedule`() {
        val anchor = LocalDate.of(2026, 1, 1)
        val since = ZonedDateTime.of(2026, 6, 1, 8, 0, 0, 0, zone)
        val now = ZonedDateTime.of(2026, 6, 1, 8, 30, 0, 0, zone) // same day, right after the one fire

        val occurrences = CalendarRecurrenceEngine.occurrencesBetween(
            freq = RecurrenceFrequency.DAILY,
            interval = 1,
            daysOfWeek = emptySet(),
            dayOfMonth = null,
            timesOfDay = listOf(LocalTime.of(8, 0)),
            endDate = null,
            anchorDate = anchor,
            zone = zone,
            since = since,
            now = now
        )
        val resolution = CalendarRecurrenceEngine.resolveMissed(occurrences)
        assertTrue(resolution.toAutoSkip.isEmpty())
        assertEquals(LocalDate.of(2026, 6, 1), resolution.current?.toLocalDate())
    }

    @Test
    fun `empty window resolves to no current and nothing to skip`() {
        val resolution = CalendarRecurrenceEngine.resolveMissed(emptyList())
        assertTrue(resolution.toAutoSkip.isEmpty())
        assertNull(resolution.current)
    }
}
