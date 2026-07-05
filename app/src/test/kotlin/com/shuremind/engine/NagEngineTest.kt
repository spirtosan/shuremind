package com.shuremind.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class NagEngineTest {

    private val zone: ZoneId = ZoneId.of("Europe/Sofia")

    // --- Acceptance test #1: dentist, not_before=2026-07-07, every 24h ---

    @Test
    fun `before not_before, next occurrence is not_before itself`() {
        val createdAt = ZonedDateTime.of(2026, 6, 1, 9, 0, 0, 0, zone)
        val now = ZonedDateTime.of(2026, 7, 1, 12, 0, 0, 0, zone) // before not_before
        val result = NagEngine.nextOccurrence(
            intervalHours = 24.0,
            anchor = createdAt,
            notBefore = LocalDate.of(2026, 7, 7),
            zone = zone,
            now = now
        )
        assertEquals(LocalDate.of(2026, 7, 7), result.toLocalDate())
        assertEquals(EngineTuning.DEFAULT_ALL_DAY_TIME, result.toLocalTime())
    }

    @Test
    fun `after not_before, nags every 24h until done`() {
        val createdAt = ZonedDateTime.of(2026, 6, 1, 9, 0, 0, 0, zone)
        val notBefore = LocalDate.of(2026, 7, 7)
        val notBeforeInstant = ZonedDateTime.of(2026, 7, 7, 9, 0, 0, 0, zone)

        // Exactly one day after the not_before instant, still nagging.
        val now = notBeforeInstant.plusHours(30)
        val result = NagEngine.nextOccurrence(
            intervalHours = 24.0,
            anchor = createdAt,
            notBefore = notBefore,
            zone = zone,
            now = now
        )
        assertEquals(notBeforeInstant.plusHours(48), result)
    }

    @Test
    fun `steps forward correctly across many missed intervals at once`() {
        val anchor = ZonedDateTime.of(2026, 1, 1, 9, 0, 0, 0, zone)
        val now = anchor.plusHours(24 * 10 + 5) // 10 days and 5 hours later, never checked in between
        val result = NagEngine.nextOccurrence(
            intervalHours = 24.0,
            anchor = anchor,
            notBefore = null,
            zone = zone,
            now = now
        )
        assertEquals(anchor.plusDays(11), result)
        assertFalse(result.isBefore(now))
    }

    @Test
    fun `result is always strictly after now once nagging has started`() {
        val anchor = ZonedDateTime.of(2026, 1, 1, 9, 0, 0, 0, zone)
        val now = anchor.plusHours(48) // lands exactly on a nag slot
        val result = NagEngine.nextOccurrence(
            intervalHours = 24.0,
            anchor = anchor,
            notBefore = null,
            zone = zone,
            now = now
        )
        assertEquals(anchor.plusDays(3), result)
    }
}
