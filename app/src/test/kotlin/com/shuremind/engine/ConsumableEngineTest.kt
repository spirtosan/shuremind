package com.shuremind.engine

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class ConsumableEngineTest {

    private val zone: ZoneId = ZoneId.of("Europe/Sofia")

    // --- Acceptance test #7: stock=30, dose 2/day, lead 5 days -> reminder ~10 days later ---

    @Test
    fun `run-out date is stockRecordedAt plus floor of stock over daily consumption`() {
        val stockRecordedAt = LocalDate.of(2026, 6, 1)
        val runOut = ConsumableEngine.computeRunOutDate(stockRecordedAt, stockQty = 30.0, dosePerIntake = 1.0, intakesPerDay = 2)
        assertEquals(LocalDate.of(2026, 6, 16), runOut) // stockRecordedAt + floor(30/2)=15
    }

    @Test
    fun `restock reminder is run-out minus lead days`() {
        val stockRecordedAt = LocalDate.of(2026, 6, 1)
        val runOut = ConsumableEngine.computeRunOutDate(stockRecordedAt, stockQty = 30.0, dosePerIntake = 1.0, intakesPerDay = 2)
        val remindDate = ConsumableEngine.computeRestockReminderDate(runOut, restockLeadDays = 5)
        assertEquals(LocalDate.of(2026, 6, 11), remindDate) // ~10 days after stockRecordedAt
    }

    @Test
    fun `run-out date floors a non-exact division`() {
        val stockRecordedAt = LocalDate.of(2026, 6, 1)
        // 10 units at 3 per day -> 3 full days remaining, not 3.33
        val runOut = ConsumableEngine.computeRunOutDate(stockRecordedAt, stockQty = 10.0, dosePerIntake = 3.0, intakesPerDay = 1)
        assertEquals(LocalDate.of(2026, 6, 4), runOut)
    }

    @Test
    fun `before the reminder date, next occurrence is the reminder instant`() {
        val stockRecordedAt = LocalDate.of(2026, 6, 1)
        val now = ZonedDateTime.of(2026, 6, 1, 8, 0, 0, 0, zone)
        val result = ConsumableEngine.nextOccurrence(
            stockRecordedAt = stockRecordedAt,
            stockQty = 30.0,
            dosePerIntake = 1.0,
            intakesPerDay = 2,
            restockLeadDays = 5,
            zone = zone,
            now = now
        )
        assertEquals(LocalDate.of(2026, 6, 11), result.toLocalDate())
        assertEquals(EngineTuning.DEFAULT_ALL_DAY_TIME, result.toLocalTime())
    }

    @Test
    fun `after the reminder date passes, nags daily until restocked`() {
        // Stock level was recorded June 1st and hasn't changed; only 'now' has advanced past
        // the June 11 reminder date computed from that same stock reading.
        val stockRecordedAt = LocalDate.of(2026, 6, 1)
        val now = ZonedDateTime.of(2026, 6, 12, 10, 0, 0, 0, zone)
        val result = ConsumableEngine.nextOccurrence(
            stockRecordedAt = stockRecordedAt,
            stockQty = 30.0,
            dosePerIntake = 1.0,
            intakesPerDay = 2,
            restockLeadDays = 5,
            zone = zone,
            now = now
        )
        // Reminder was June 11 09:00; now is June 12 10:00 (past it) -> next nag slot is June 13 09:00
        assertEquals(LocalDate.of(2026, 6, 13), result.toLocalDate())
    }

    // --- D-26: remainingStock (restock flow) ---

    @Test
    fun `remainingStock subtracts consumption since stockRecordedAt`() {
        val stockRecordedAt = LocalDate.of(2026, 6, 1)
        val today = LocalDate.of(2026, 6, 6) // 5 days elapsed, 2 per day = 10 consumed
        val remaining = ConsumableEngine.remainingStock(stockRecordedAt, stockQty = 30.0, dosePerIntake = 1.0, intakesPerDay = 2, today = today)
        assertEquals(20.0, remaining, 0.0)
    }

    @Test
    fun `remainingStock clamps at zero once past run-out`() {
        val stockRecordedAt = LocalDate.of(2026, 6, 1)
        val today = LocalDate.of(2026, 7, 1) // well past the 15-day run-out
        val remaining = ConsumableEngine.remainingStock(stockRecordedAt, stockQty = 30.0, dosePerIntake = 1.0, intakesPerDay = 2, today = today)
        assertEquals(0.0, remaining, 0.0)
    }

    @Test
    fun `remainingStock on the recorded day itself equals the recorded quantity`() {
        val stockRecordedAt = LocalDate.of(2026, 6, 1)
        val remaining = ConsumableEngine.remainingStock(stockRecordedAt, stockQty = 30.0, dosePerIntake = 1.0, intakesPerDay = 2, today = stockRecordedAt)
        assertEquals(30.0, remaining, 0.0)
    }

    @Test
    fun `next occurrence stays fixed between recomputes as now advances, given the same stock reading`() {
        val stockRecordedAt = LocalDate.of(2026, 6, 1)
        val earlyNow = ZonedDateTime.of(2026, 6, 2, 8, 0, 0, 0, zone)
        val laterNow = ZonedDateTime.of(2026, 6, 9, 8, 0, 0, 0, zone)

        val early = ConsumableEngine.nextOccurrence(stockRecordedAt, 30.0, 1.0, 2, 5, zone, earlyNow)
        val later = ConsumableEngine.nextOccurrence(stockRecordedAt, 30.0, 1.0, 2, 5, zone, laterNow)

        assertEquals(early, later) // both still before the June 11 reminder -> same cached instant
    }
}
