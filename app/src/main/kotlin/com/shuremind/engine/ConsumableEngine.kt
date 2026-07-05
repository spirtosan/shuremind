package com.shuremind.engine

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.floor
import kotlin.math.max

/** CONSUMABLE: computed run-out date from stock + dose, remind N days before, then daily nag until restocked. */
object ConsumableEngine {

    /** run_out_date = stockRecordedAt + floor(stock_qty / (dose_per_intake x intakes_per_day)) */
    fun computeRunOutDate(stockRecordedAt: LocalDate, stockQty: Double, dosePerIntake: Double, intakesPerDay: Int): LocalDate {
        require(dosePerIntake > 0) { "dosePerIntake must be positive" }
        require(intakesPerDay > 0) { "intakesPerDay must be positive" }
        val dailyConsumption = dosePerIntake * intakesPerDay
        val daysRemaining = floor(stockQty / dailyConsumption).toLong()
        return stockRecordedAt.plusDays(daysRemaining)
    }

    /**
     * D-26: stock remaining as of [today], given consumption since [stockRecordedAt] — clamped to 0
     * (never negative; a task left unattended well past run-out still reports "0 remaining", not a
     * negative deficit).
     */
    fun remainingStock(stockRecordedAt: LocalDate, stockQty: Double, dosePerIntake: Double, intakesPerDay: Int, today: LocalDate): Double {
        require(dosePerIntake > 0) { "dosePerIntake must be positive" }
        require(intakesPerDay > 0) { "intakesPerDay must be positive" }
        val daysElapsed = max(0L, ChronoUnit.DAYS.between(stockRecordedAt, today))
        val consumed = dosePerIntake * intakesPerDay * daysElapsed
        return max(0.0, stockQty - consumed)
    }

    fun computeRestockReminderDate(runOutDate: LocalDate, restockLeadDays: Int): LocalDate =
        runOutDate.minusDays(restockLeadDays.toLong())

    /**
     * Reminder fires at run_out - restock_lead_days; once passed, nags daily until stock is updated.
     *
     * [stockRecordedAt] anchors the run-out math to the date [stockQty] was last known true (task
     * creation or last edit) — NOT to [now]. next_fire_at is a derived cache recomputed only when
     * the task is edited/completed/boots/etc, so the reminder date must stay fixed between recomputes
     * rather than perpetually sliding forward as time passes.
     */
    fun nextOccurrence(
        stockRecordedAt: LocalDate,
        stockQty: Double,
        dosePerIntake: Double,
        intakesPerDay: Int,
        restockLeadDays: Int,
        zone: ZoneId,
        now: ZonedDateTime
    ): ZonedDateTime {
        val runOutDate = computeRunOutDate(stockRecordedAt, stockQty, dosePerIntake, intakesPerDay)
        val remindDate = computeRestockReminderDate(runOutDate, restockLeadDays)
        val remindInstant = LocalDateTime.of(remindDate, EngineTuning.DEFAULT_ALL_DAY_TIME).atZone(zone)
        return if (now.isBefore(remindInstant)) {
            remindInstant
        } else {
            NagEngine.nextOccurrence(intervalHours = 24.0, anchor = remindInstant, notBefore = null, zone = zone, now = now)
        }
    }
}
