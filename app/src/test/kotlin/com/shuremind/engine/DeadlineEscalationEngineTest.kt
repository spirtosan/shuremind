package com.shuremind.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** DEADLINE-only escalation curve (DATA_MODEL.md "Deadline escalation"): 1/day -> 2/day -> 3/day -> every 4h overdue. */
class DeadlineEscalationEngineTest {

    private val zone: ZoneId = ZoneId.of("Europe/Sofia")
    private val dueInstant: ZonedDateTime = ZonedDateTime.of(2026, 8, 15, 10, 0, 0, 0, zone)
    private val defaultAllDayTime: LocalTime = LocalTime.of(9, 0)
    private val quietHoursEnd: LocalTime = LocalTime.of(8, 0)

    private fun slot(now: ZonedDateTime) =
        DeadlineEscalationEngine.nextSlot(dueInstant, now, zone, defaultAllDayTime, quietHoursEnd)

    @Test
    fun `more than 14 days out, no escalation slot yet`() {
        val now = dueInstant.minusDays(15).withHour(0).withMinute(0)
        assertNull(slot(now))
    }

    @Test
    fun `exactly 14 days out, escalation begins at one slot per day`() {
        val now = dueInstant.minusDays(14).withHour(0).withMinute(0)
        assertEquals(dueInstant.minusDays(14).withHour(9).withMinute(0), slot(now))
    }

    @Test
    fun `within 7 days, two slots per day at 09-00 and 18-00`() {
        val dayStart = dueInstant.minusDays(7).withHour(0).withMinute(0)
        assertEquals(dueInstant.minusDays(7).withHour(9).withMinute(0), slot(dayStart))

        val afterFirstSlot = dueInstant.minusDays(7).withHour(10).withMinute(0)
        assertEquals(dueInstant.minusDays(7).withHour(18).withMinute(0), slot(afterFirstSlot))
    }

    @Test
    fun `within 2 days, three slots per day at 09-14 and 19`() {
        val dayStart = dueInstant.minusDays(2).withHour(0).withMinute(0)
        assertEquals(dueInstant.minusDays(2).withHour(9).withMinute(0), slot(dayStart))
    }

    @Test
    fun `once overdue, escalates every 4h phase-aligned to quiet-hours end`() {
        // Grid anchored at quiet-hours-end (08:00) on the due date: 08, 12, 16, 20, 00, 04...
        val justPastDue = dueInstant.plusHours(1) // due is 10:00, now 11:00
        assertEquals(dueInstant.withHour(12).withMinute(0), slot(justPastDue))
    }

    @Test
    fun `exactly at the due instant already counts as overdue`() {
        assertEquals(dueInstant.withHour(12).withMinute(0), slot(dueInstant))
    }
}
