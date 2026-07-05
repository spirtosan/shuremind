package com.shuremind.engine

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** D-12: any fire instant landing inside the global quiet window defers to the window's end. */
class QuietHoursTest {

    private val zone: ZoneId = ZoneId.of("Europe/Sofia")
    private val start = LocalTime.of(22, 0)
    private val end = LocalTime.of(8, 0)

    @Test
    fun `late evening instant defers to quiet-hours end the next day`() {
        val instant = ZonedDateTime.of(2026, 6, 1, 23, 30, 0, 0, zone)
        val result = QuietHours.deferIfInside(instant, start, end, zone)
        assertEquals(ZonedDateTime.of(2026, 6, 2, 8, 0, 0, 0, zone), result)
    }

    @Test
    fun `early morning instant defers to quiet-hours end the same day`() {
        val instant = ZonedDateTime.of(2026, 6, 2, 3, 0, 0, 0, zone)
        val result = QuietHours.deferIfInside(instant, start, end, zone)
        assertEquals(ZonedDateTime.of(2026, 6, 2, 8, 0, 0, 0, zone), result)
    }

    @Test
    fun `exactly at quiet-hours end is not deferred (half-open window)`() {
        val instant = ZonedDateTime.of(2026, 6, 2, 8, 0, 0, 0, zone)
        val result = QuietHours.deferIfInside(instant, start, end, zone)
        assertEquals(instant, result)
    }

    @Test
    fun `exactly at quiet-hours start is deferred (half-open window)`() {
        val instant = ZonedDateTime.of(2026, 6, 1, 22, 0, 0, 0, zone)
        val result = QuietHours.deferIfInside(instant, start, end, zone)
        assertEquals(ZonedDateTime.of(2026, 6, 2, 8, 0, 0, 0, zone), result)
    }

    @Test
    fun `instant outside the window is unchanged`() {
        val instant = ZonedDateTime.of(2026, 6, 1, 12, 0, 0, 0, zone)
        val result = QuietHours.deferIfInside(instant, start, end, zone)
        assertEquals(instant, result)
    }

    @Test
    fun `non-wrapping window (start before end) defers within the same day`() {
        val dayStart = LocalTime.of(8, 0)
        val dayEnd = LocalTime.of(22, 0)
        val inside = ZonedDateTime.of(2026, 6, 1, 10, 0, 0, 0, zone)
        val outside = ZonedDateTime.of(2026, 6, 1, 23, 0, 0, 0, zone)

        assertEquals(ZonedDateTime.of(2026, 6, 1, 22, 0, 0, 0, zone), QuietHours.deferIfInside(inside, dayStart, dayEnd, zone))
        assertEquals(outside, QuietHours.deferIfInside(outside, dayStart, dayEnd, zone))
    }

    @Test
    fun `zero-width window disables quiet hours entirely`() {
        val instant = ZonedDateTime.of(2026, 6, 1, 23, 30, 0, 0, zone)
        val result = QuietHours.deferIfInside(instant, LocalTime.of(9, 0), LocalTime.of(9, 0), zone)
        assertEquals(instant, result)
    }
}
