package com.shuremind.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class MeterEngineTest {

    private val zone: ZoneId = ZoneId.of("Europe/Sofia")

    // --- Acceptance test #3: car oil, 12 months + every 10,000 km, due when EITHER threshold crossed ---

    @Test
    fun `meter is due once the distance since last completion reaches the interval`() {
        assertTrue(MeterEngine.isMeterDue(latestMeterValue = 60_000.0, lastDoneMeter = 50_000.0, meterInterval = 10_000.0))
    }

    @Test
    fun `meter is due exactly at the interval boundary (inclusive)`() {
        assertTrue(MeterEngine.isMeterDue(latestMeterValue = 60_000.0, lastDoneMeter = 50_000.0, meterInterval = 10_000.0))
        assertFalse(MeterEngine.isMeterDue(latestMeterValue = 59_999.0, lastDoneMeter = 50_000.0, meterInterval = 10_000.0))
    }

    @Test
    fun `isDue is true when the meter alone crosses the threshold, time not yet due`() {
        val now = ZonedDateTime.of(2026, 6, 1, 9, 0, 0, 0, zone)
        val futureTimeOccurrence = now.plusMonths(3)
        assertTrue(MeterEngine.isDue(timeOccurrence = futureTimeOccurrence, now = now, meterDue = true))
    }

    @Test
    fun `isDue is true when time alone is due, meter not yet due`() {
        val now = ZonedDateTime.of(2026, 6, 1, 9, 0, 0, 0, zone)
        val pastTimeOccurrence = now.minusDays(1)
        assertTrue(MeterEngine.isDue(timeOccurrence = pastTimeOccurrence, now = now, meterDue = false))
    }

    @Test
    fun `isDue is false when neither time nor meter is due`() {
        val now = ZonedDateTime.of(2026, 6, 1, 9, 0, 0, 0, zone)
        val futureTimeOccurrence = now.plusMonths(3)
        assertFalse(MeterEngine.isDue(timeOccurrence = futureTimeOccurrence, now = now, meterDue = false))
    }

    @Test
    fun `isDue is false with no time occurrence and meter not due`() {
        val now = ZonedDateTime.of(2026, 6, 1, 9, 0, 0, 0, zone)
        assertFalse(MeterEngine.isDue(timeOccurrence = null, now = now, meterDue = false))
    }
}
