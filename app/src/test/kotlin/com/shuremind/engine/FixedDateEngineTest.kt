package com.shuremind.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class FixedDateEngineTest {

    private val zone: ZoneId = ZoneId.of("Europe/Sofia")

    // --- EVENT / DEADLINE ---

    @Test
    fun `fixedOccurrence combines date and time`() {
        val result = FixedDateEngine.fixedOccurrence(LocalDate.of(2026, 8, 20), LocalTime.of(14, 30), zone)
        assertEquals(ZonedDateTime.of(2026, 8, 20, 14, 30, 0, 0, zone), result)
    }

    @Test
    fun `fixedOccurrence defaults to all-day time when time is null`() {
        val result = FixedDateEngine.fixedOccurrence(LocalDate.of(2026, 8, 20), null, zone)
        assertEquals(EngineTuning.DEFAULT_ALL_DAY_TIME, result?.toLocalTime())
    }

    @Test
    fun `fixedOccurrence returns null when due date is missing`() {
        assertNull(FixedDateEngine.fixedOccurrence(null, LocalTime.of(9, 0), zone))
    }

    // --- ANNIVERSARY ---

    @Test
    fun `anniversary still upcoming this year returns this year`() {
        val dueDate = LocalDate.of(2000, 9, 15) // year is irrelevant, MM-DD reused yearly
        val now = ZonedDateTime.of(2026, 7, 5, 12, 0, 0, 0, zone)
        val result = FixedDateEngine.anniversaryOccurrence(dueDate, LocalTime.of(9, 0), zone, now)
        assertEquals(LocalDate.of(2026, 9, 15), result.toLocalDate())
    }

    @Test
    fun `anniversary already passed this year advances to next year`() {
        val dueDate = LocalDate.of(2000, 3, 1)
        val now = ZonedDateTime.of(2026, 7, 5, 12, 0, 0, 0, zone)
        val result = FixedDateEngine.anniversaryOccurrence(dueDate, LocalTime.of(9, 0), zone, now)
        assertEquals(LocalDate.of(2027, 3, 1), result.toLocalDate())
    }

    @Test
    fun `anniversary on Feb 29 clamps to Feb 28 in a non-leap year`() {
        val dueDate = LocalDate.of(2000, 2, 29)
        val now = ZonedDateTime.of(2026, 1, 1, 0, 0, 0, 0, zone) // 2026 is not a leap year
        val result = FixedDateEngine.anniversaryOccurrence(dueDate, LocalTime.of(9, 0), zone, now)
        assertEquals(LocalDate.of(2026, 2, 28), result.toLocalDate())
    }

    @Test
    fun `anniversary on Feb 29 uses the real date in a leap year`() {
        val dueDate = LocalDate.of(2000, 2, 29)
        val now = ZonedDateTime.of(2027, 6, 1, 0, 0, 0, 0, zone) // 2028 is a leap year
        val result = FixedDateEngine.anniversaryOccurrence(dueDate, LocalTime.of(9, 0), zone, now)
        assertEquals(LocalDate.of(2028, 2, 29), result.toLocalDate())
    }

    @Test
    fun `clampedDate clamps day-of-month to the last valid day`() {
        assertEquals(LocalDate.of(2026, 4, 30), FixedDateEngine.clampedDate(2026, 4, 31))
        assertEquals(LocalDate.of(2026, 2, 28), FixedDateEngine.clampedDate(2026, 2, 29))
        assertEquals(LocalDate.of(2028, 2, 29), FixedDateEngine.clampedDate(2028, 2, 29))
    }
}
