package com.shuremind.engine

import java.time.ZoneId
import java.time.ZonedDateTime

/** Top-level dispatcher: computes the next fire instant for a task, routed by behavior type. */
object OccurrenceEngine {

    /**
     * @param createdAt used as the CALENDAR-grid anchor fallback (when no due date is set), the
     *   COMPLETION-anchor fallback when the task has never been done, and the CONSUMABLE
     *   stock-recorded-at fallback when the task predates D-19 and has none set.
     * @param lastDoneAt last DONE completion instant, for COMPLETION-anchor recurrence.
     * Returns null for SOMEDAY (never fires) or when required type-specific fields are missing.
     */
    fun nextOccurrence(
        schedule: TaskSchedule,
        zone: ZoneId,
        now: ZonedDateTime,
        createdAt: ZonedDateTime,
        lastDoneAt: ZonedDateTime? = null
    ): ZonedDateTime? = when (schedule.type) {
        TaskType.EVENT, TaskType.DEADLINE ->
            FixedDateEngine.fixedOccurrence(schedule.dueLocalDate, schedule.dueLocalTime, zone)

        TaskType.ANNIVERSARY ->
            schedule.dueLocalDate?.let {
                FixedDateEngine.anniversaryOccurrence(it, schedule.dueLocalTime, zone, now)
            }

        TaskType.WINDOW ->
            schedule.recFreq?.let { freq ->
                CalendarRecurrenceEngine.nextOccurrence(
                    freq = freq,
                    interval = schedule.recInterval,
                    daysOfWeek = schedule.recDaysOfWeek,
                    dayOfMonth = schedule.recDayOfMonth,
                    timesOfDay = schedule.recTimesOfDay,
                    endDate = schedule.recEndDate,
                    anchorDate = schedule.dueLocalDate ?: createdAt.toLocalDate(),
                    zone = zone,
                    now = now,
                    notBefore = schedule.notBefore
                )
            }

        TaskType.NAG ->
            schedule.nagIntervalHours?.let { hours ->
                NagEngine.nextOccurrence(hours, createdAt, schedule.notBefore, zone, now)
            }

        TaskType.RECURRING -> when (schedule.recAnchor) {
            RecurrenceAnchor.CALENDAR -> schedule.recFreq?.let { freq ->
                CalendarRecurrenceEngine.nextOccurrence(
                    freq = freq,
                    interval = schedule.recInterval,
                    daysOfWeek = schedule.recDaysOfWeek,
                    dayOfMonth = schedule.recDayOfMonth,
                    timesOfDay = schedule.recTimesOfDay,
                    endDate = schedule.recEndDate,
                    anchorDate = schedule.dueLocalDate ?: createdAt.toLocalDate(),
                    zone = zone,
                    now = now,
                    notBefore = schedule.notBefore
                )
            }
            RecurrenceAnchor.COMPLETION -> schedule.recFreq?.let { freq ->
                CompletionRecurrenceEngine.nextOccurrence(
                    freq = freq,
                    interval = schedule.recInterval,
                    timesOfDay = schedule.recTimesOfDay,
                    lastAnchor = lastDoneAt ?: createdAt,
                    zone = zone
                )
            }
            null -> null
        }

        TaskType.CONSUMABLE -> {
            val stockQty = schedule.stockQty
            val dosePerIntake = schedule.dosePerIntake
            val restockLeadDays = schedule.restockLeadDays
            if (stockQty != null && dosePerIntake != null && restockLeadDays != null) {
                ConsumableEngine.nextOccurrence(
                    stockRecordedAt = schedule.stockRecordedAt ?: createdAt.toLocalDate(),
                    stockQty = stockQty,
                    dosePerIntake = dosePerIntake,
                    intakesPerDay = schedule.recTimesOfDay.size.coerceAtLeast(1),
                    restockLeadDays = restockLeadDays,
                    zone = zone,
                    now = now
                )
            } else {
                null
            }
        }

        TaskType.SOMEDAY -> null
    }

    /**
     * Meter-based maintenance (acceptance test #3, car oil): due when EITHER the time-based
     * occurrence has arrived OR the meter threshold has been crossed — never meter-only, since a
     * task with meter fields set can still have an earlier time-based due date (e.g. the 12-month
     * service interval firing before 10,000 km is reached).
     *
     * [latestMeterValue] is the most recent MeterReading for this task's meter (queried by the
     * caller; engine has no DB access). Null (no reading yet) means the meter leg is never due.
     */
    fun isDue(
        schedule: TaskSchedule,
        zone: ZoneId,
        now: ZonedDateTime,
        createdAt: ZonedDateTime,
        lastDoneAt: ZonedDateTime? = null,
        latestMeterValue: Double? = null
    ): Boolean {
        val timeOccurrence = nextOccurrence(schedule, zone, now, createdAt, lastDoneAt)
        val lastDoneMeter = schedule.lastDoneMeter
        val meterInterval = schedule.meterInterval
        val meterDue = latestMeterValue != null && lastDoneMeter != null && meterInterval != null &&
            MeterEngine.isMeterDue(latestMeterValue, lastDoneMeter, meterInterval)
        return MeterEngine.isDue(timeOccurrence, now, meterDue)
    }
}
