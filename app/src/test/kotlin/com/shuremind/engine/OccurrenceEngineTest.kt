package com.shuremind.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** Cross-cutting dispatcher test: all 8 behaviors route to the correct sub-engine. */
class OccurrenceEngineTest {

    private val zone: ZoneId = ZoneId.of("Europe/Sofia")
    private val createdAt: ZonedDateTime = ZonedDateTime.of(2026, 1, 1, 9, 0, 0, 0, zone)
    private val now: ZonedDateTime = ZonedDateTime.of(2026, 6, 1, 9, 0, 0, 0, zone)

    @Test
    fun `EVENT dispatches to the fixed-instant engine`() {
        val schedule = TaskSchedule(
            type = TaskType.EVENT,
            dueLocalDate = LocalDate.of(2026, 8, 20),
            dueLocalTime = LocalTime.of(14, 0)
        )
        val result = OccurrenceEngine.nextOccurrence(schedule, zone, now, createdAt)
        assertEquals(ZonedDateTime.of(2026, 8, 20, 14, 0, 0, 0, zone), result)
    }

    @Test
    fun `DEADLINE dispatches to the fixed-instant engine`() {
        // Acceptance test #4: property tax deadline.
        val schedule = TaskSchedule(type = TaskType.DEADLINE, dueLocalDate = LocalDate.of(2026, 12, 31))
        val result = OccurrenceEngine.nextOccurrence(schedule, zone, now, createdAt)
        assertEquals(LocalDate.of(2026, 12, 31), result?.toLocalDate())
    }

    @Test
    fun `ANNIVERSARY dispatches to the yearly engine`() {
        // Acceptance test #9: birthday.
        val schedule = TaskSchedule(type = TaskType.ANNIVERSARY, dueLocalDate = LocalDate.of(1990, 9, 15))
        val result = OccurrenceEngine.nextOccurrence(schedule, zone, now, createdAt)
        assertEquals(LocalDate.of(2026, 9, 15), result?.toLocalDate())
    }

    @Test
    fun `WINDOW dispatches to the CALENDAR grid engine`() {
        val schedule = TaskSchedule(
            type = TaskType.WINDOW,
            dueLocalDate = LocalDate.of(2026, 9, 1),
            recFreq = RecurrenceFrequency.MONTHLY,
            recDayOfMonth = 1,
            recTimesOfDay = listOf(LocalTime.of(9, 0))
        )
        val result = OccurrenceEngine.nextOccurrence(schedule, zone, ZonedDateTime.of(2026, 9, 2, 0, 0, 0, 0, zone), createdAt)
        assertEquals(LocalDate.of(2026, 10, 1), result?.toLocalDate())
    }

    @Test
    fun `NAG dispatches to the interval-stepping engine`() {
        // Acceptance test #1: dentist.
        val schedule = TaskSchedule(
            type = TaskType.NAG,
            notBefore = LocalDate.of(2026, 7, 7),
            nagIntervalHours = 24.0
        )
        val result = OccurrenceEngine.nextOccurrence(schedule, zone, ZonedDateTime.of(2026, 6, 1, 9, 0, 0, 0, zone), createdAt)
        assertEquals(LocalDate.of(2026, 7, 7), result?.toLocalDate())
    }

    @Test
    fun `RECURRING CALENDAR dispatches to the grid engine`() {
        // Acceptance test #6: meds intake.
        val schedule = TaskSchedule(
            type = TaskType.RECURRING,
            recAnchor = RecurrenceAnchor.CALENDAR,
            recFreq = RecurrenceFrequency.DAILY,
            recTimesOfDay = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0))
        )
        val result = OccurrenceEngine.nextOccurrence(schedule, zone, ZonedDateTime.of(2026, 6, 1, 10, 0, 0, 0, zone), createdAt)
        assertEquals(ZonedDateTime.of(2026, 6, 1, 20, 0, 0, 0, zone), result)
    }

    @Test
    fun `RECURRING COMPLETION dispatches to the completion engine`() {
        // Acceptance test #8: flowers every 3 days.
        val schedule = TaskSchedule(
            type = TaskType.RECURRING,
            recAnchor = RecurrenceAnchor.COMPLETION,
            recFreq = RecurrenceFrequency.DAILY,
            recInterval = 3
        )
        val lastDoneAt = ZonedDateTime.of(2026, 5, 30, 9, 0, 0, 0, zone)
        val result = OccurrenceEngine.nextOccurrence(schedule, zone, now, createdAt, lastDoneAt)
        assertEquals(LocalDate.of(2026, 6, 2), result?.toLocalDate())
    }

    @Test
    fun `CONSUMABLE dispatches to the run-out engine`() {
        // Acceptance test #7: secondary user's meds stock.
        val schedule = TaskSchedule(
            type = TaskType.CONSUMABLE,
            stockQty = 30.0,
            dosePerIntake = 1.0,
            restockLeadDays = 5,
            recTimesOfDay = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)) // 2 intakes/day
        )
        val result = OccurrenceEngine.nextOccurrence(schedule, zone, ZonedDateTime.of(2026, 1, 2, 0, 0, 0, 0, zone), createdAt)
        assertEquals(LocalDate.of(2026, 1, 11), result?.toLocalDate())
    }

    @Test
    fun `CONSUMABLE with missing stock fields returns null`() {
        val schedule = TaskSchedule(type = TaskType.CONSUMABLE)
        assertNull(OccurrenceEngine.nextOccurrence(schedule, zone, now, createdAt))
    }

    @Test
    fun `CONSUMABLE anchors run-out math to stockRecordedAt, not createdAt`() {
        // D-19: a restock long after creation must re-anchor the run-out date, or the
        // reminder would stay stuck on the original createdAt-based schedule forever.
        val schedule = TaskSchedule(
            type = TaskType.CONSUMABLE,
            stockQty = 30.0,
            dosePerIntake = 1.0,
            restockLeadDays = 5,
            recTimesOfDay = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)),
            stockRecordedAt = LocalDate.of(2026, 6, 1)
        )
        val result = OccurrenceEngine.nextOccurrence(schedule, zone, ZonedDateTime.of(2026, 6, 2, 0, 0, 0, 0, zone), createdAt)
        assertEquals(LocalDate.of(2026, 6, 11), result?.toLocalDate())
    }

    @Test
    fun `CONSUMABLE falls back to createdAt when stockRecordedAt is null`() {
        // Pre-D-19 tasks (or any edge case where the anchor was never set) must still work.
        val schedule = TaskSchedule(
            type = TaskType.CONSUMABLE,
            stockQty = 30.0,
            dosePerIntake = 1.0,
            restockLeadDays = 5,
            recTimesOfDay = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)),
            stockRecordedAt = null
        )
        val result = OccurrenceEngine.nextOccurrence(schedule, zone, ZonedDateTime.of(2026, 1, 2, 0, 0, 0, 0, zone), createdAt)
        assertEquals(LocalDate.of(2026, 1, 11), result?.toLocalDate())
    }

    @Test
    fun `SOMEDAY never fires`() {
        // Acceptance test #2: shower tray.
        val schedule = TaskSchedule(type = TaskType.SOMEDAY)
        assertNull(OccurrenceEngine.nextOccurrence(schedule, zone, now, createdAt))
    }

    @Test
    fun `RECURRING with no anchor set returns null`() {
        val schedule = TaskSchedule(type = TaskType.RECURRING, recFreq = RecurrenceFrequency.DAILY, recAnchor = null)
        assertNull(OccurrenceEngine.nextOccurrence(schedule, zone, now, createdAt))
    }

    // --- Acceptance test #3: car oil, RECURRING COMPLETION 12 months + meter every 10,000 km ---

    private val carOilSchedule = TaskSchedule(
        type = TaskType.RECURRING,
        recAnchor = RecurrenceAnchor.COMPLETION,
        recFreq = RecurrenceFrequency.MONTHLY,
        recInterval = 12,
        meterInterval = 10_000.0,
        lastDoneMeter = 50_000.0
    )
    private val lastDoneAt: ZonedDateTime = ZonedDateTime.of(2026, 1, 1, 9, 0, 0, 0, zone)

    @Test
    fun `isDue is true when only the meter threshold is crossed, time not yet due`() {
        val result = OccurrenceEngine.isDue(
            schedule = carOilSchedule,
            zone = zone,
            now = now, // well within the 12-month window
            createdAt = createdAt,
            lastDoneAt = lastDoneAt,
            latestMeterValue = 61_000.0 // 11,000 km since last done, over the 10,000 interval
        )
        assertEquals(true, result)
    }

    @Test
    fun `isDue is true when only the time interval is due, meter not yet due`() {
        val result = OccurrenceEngine.isDue(
            schedule = carOilSchedule,
            zone = zone,
            now = ZonedDateTime.of(2027, 2, 1, 9, 0, 0, 0, zone), // over 12 months since lastDoneAt
            createdAt = createdAt,
            lastDoneAt = lastDoneAt,
            latestMeterValue = 55_000.0 // only 5,000 km, under the 10,000 interval
        )
        assertEquals(true, result)
    }

    @Test
    fun `isDue is false when neither time nor meter threshold is crossed`() {
        val result = OccurrenceEngine.isDue(
            schedule = carOilSchedule,
            zone = zone,
            now = now,
            createdAt = createdAt,
            lastDoneAt = lastDoneAt,
            latestMeterValue = 55_000.0
        )
        assertEquals(false, result)
    }

    @Test
    fun `isDue never treats a task as due from the meter alone when meter fields are missing`() {
        val schedule = TaskSchedule(
            type = TaskType.RECURRING,
            recAnchor = RecurrenceAnchor.COMPLETION,
            recFreq = RecurrenceFrequency.MONTHLY,
            recInterval = 12
            // no meterInterval / lastDoneMeter set
        )
        val result = OccurrenceEngine.isDue(
            schedule = schedule,
            zone = zone,
            now = now,
            createdAt = createdAt,
            lastDoneAt = lastDoneAt,
            latestMeterValue = 999_999.0 // huge reading, must be ignored without meter fields
        )
        assertEquals(false, result)
    }
}
