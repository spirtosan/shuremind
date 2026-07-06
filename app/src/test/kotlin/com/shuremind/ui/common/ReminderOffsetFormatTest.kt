package com.shuremind.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderOffsetFormatTest {

    // --- ISO -> display unit (D-37) ---

    @Test
    fun `P14D parses to 2 weeks, not 14 days`() {
        assertEquals(ReminderOffsetValue.Before(2, OffsetUnit.WEEKS), ReminderOffsetFormat.parse("P14D"))
    }

    @Test
    fun `P7D parses to 1 week`() {
        assertEquals(ReminderOffsetValue.Before(1, OffsetUnit.WEEKS), ReminderOffsetFormat.parse("P7D"))
    }

    @Test
    fun `P3D parses to 3 days`() {
        assertEquals(ReminderOffsetValue.Before(3, OffsetUnit.DAYS), ReminderOffsetFormat.parse("P3D"))
    }

    @Test
    fun `P1D parses to 1 day`() {
        assertEquals(ReminderOffsetValue.Before(1, OffsetUnit.DAYS), ReminderOffsetFormat.parse("P1D"))
    }

    @Test
    fun `PT2H parses to 2 hours`() {
        assertEquals(ReminderOffsetValue.Before(2, OffsetUnit.HOURS), ReminderOffsetFormat.parse("PT2H"))
    }

    @Test
    fun `PT90M is not evenly an hour so stays minutes`() {
        assertEquals(ReminderOffsetValue.Before(90, OffsetUnit.MINUTES), ReminderOffsetFormat.parse("PT90M"))
    }

    @Test
    fun `PT0S parses to at-due`() {
        assertEquals(ReminderOffsetValue.AtDue, ReminderOffsetFormat.parse("PT0S"))
    }

    @Test
    fun `P0D also parses to at-due since zero duration is zero regardless of unit`() {
        assertEquals(ReminderOffsetValue.AtDue, ReminderOffsetFormat.parse("P0D"))
    }

    // --- display unit -> ISO (round trip) ---

    @Test
    fun `2 weeks saves back to P14D`() {
        assertEquals("P14D", ReminderOffsetFormat.toIso(ReminderOffsetValue.Before(2, OffsetUnit.WEEKS)))
    }

    @Test
    fun `3 days saves back to P3D`() {
        assertEquals("P3D", ReminderOffsetFormat.toIso(ReminderOffsetValue.Before(3, OffsetUnit.DAYS)))
    }

    @Test
    fun `2 hours saves back to PT2H`() {
        assertEquals("PT2H", ReminderOffsetFormat.toIso(ReminderOffsetValue.Before(2, OffsetUnit.HOURS)))
    }

    @Test
    fun `at-due saves back to PT0S`() {
        assertEquals("PT0S", ReminderOffsetFormat.toIso(ReminderOffsetValue.AtDue))
    }

    @Test
    fun `round trip is stable for every unit`() {
        val values = listOf(
            ReminderOffsetValue.AtDue,
            ReminderOffsetValue.Before(1, OffsetUnit.MINUTES),
            ReminderOffsetValue.Before(45, OffsetUnit.MINUTES),
            ReminderOffsetValue.Before(1, OffsetUnit.HOURS),
            ReminderOffsetValue.Before(1, OffsetUnit.DAYS),
            ReminderOffsetValue.Before(1, OffsetUnit.WEEKS),
            ReminderOffsetValue.Before(52, OffsetUnit.WEEKS)
        )
        values.forEach { value ->
            val iso = ReminderOffsetFormat.toIso(value)
            assertEquals("round-trip failed for $value ($iso)", value, ReminderOffsetFormat.parse(iso))
        }
    }

    // --- unparseable / invalid stored values must degrade gracefully, never crash (D-37) ---

    @Test
    fun `garbage text is unparseable`() {
        assertNull(ReminderOffsetFormat.parse("not-a-duration"))
    }

    @Test
    fun `empty string is unparseable`() {
        assertNull(ReminderOffsetFormat.parse(""))
    }

    @Test
    fun `negative duration is rejected`() {
        assertNull(ReminderOffsetFormat.parse("PT-1H"))
    }

    @Test
    fun `sub-minute fractional seconds are rejected`() {
        assertNull(ReminderOffsetFormat.parse("PT0.5S"))
    }

    // --- new-input validation (D-37): integers >= 1, capped at a sane max ---

    @Test
    fun `zero amount is invalid for a Before offset`() {
        assertTrue(!ReminderOffsetFormat.isValidAmount(0, OffsetUnit.DAYS))
    }

    @Test
    fun `negative amount is invalid`() {
        assertTrue(!ReminderOffsetFormat.isValidAmount(-1, OffsetUnit.DAYS))
    }

    @Test
    fun `365 days is within the cap`() {
        assertTrue(ReminderOffsetFormat.isValidAmount(365, OffsetUnit.DAYS))
    }

    @Test
    fun `366 days exceeds the cap`() {
        assertTrue(!ReminderOffsetFormat.isValidAmount(366, OffsetUnit.DAYS))
    }

    @Test
    fun `52 weeks is within the cap`() {
        assertTrue(ReminderOffsetFormat.isValidAmount(52, OffsetUnit.WEEKS))
    }

    @Test
    fun `53 weeks exceeds the cap`() {
        assertTrue(!ReminderOffsetFormat.isValidAmount(53, OffsetUnit.WEEKS))
    }

    @Test
    fun `1 minute is valid`() {
        assertTrue(ReminderOffsetFormat.isValidAmount(1, OffsetUnit.MINUTES))
    }
}
