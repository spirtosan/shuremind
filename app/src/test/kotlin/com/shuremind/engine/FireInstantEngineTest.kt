package com.shuremind.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.Month
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.zone.ZoneOffsetTransition

class FireInstantEngineTest {

    private val zone: ZoneId = ZoneId.of("Europe/Sofia")
    private val settings = FireSettings(
        quietHoursStart = LocalTime.of(22, 0),
        quietHoursEnd = LocalTime.of(8, 0),
        defaultAllDayTime = LocalTime.of(9, 0)
    )
    private val createdAt: ZonedDateTime = ZonedDateTime.of(2026, 1, 1, 9, 0, 0, 0, zone)

    // --- (a)/(b): occurrence + reminder leads ---

    @Test
    fun `EVENT with no reminder rules fires at the occurrence itself`() {
        val input = TaskFireInput(
            taskId = "t1",
            schedule = TaskSchedule(type = TaskType.EVENT, dueLocalDate = LocalDate.of(2026, 8, 20), dueLocalTime = LocalTime.of(14, 0)),
            createdAt = createdAt
        )
        val now = ZonedDateTime.of(2026, 6, 1, 9, 0, 0, 0, zone)
        val fire = FireInstantEngine.computeNextFire(input, zone, now, settings)
        assertEquals(FireInstantEngine.FireReason.OCCURRENCE, fire?.reason)
        assertEquals(ZonedDateTime.of(2026, 8, 20, 14, 0, 0, 0, zone), fire?.at)
    }

    @Test
    fun `EVENT with P14D and P1D leads fires at the earliest pending lead`() {
        val input = TaskFireInput(
            taskId = "t1",
            schedule = TaskSchedule(type = TaskType.EVENT, dueLocalDate = LocalDate.of(2026, 8, 20), dueLocalTime = LocalTime.of(14, 0)),
            createdAt = createdAt,
            reminderOffsets = listOf("P14D", "P1D")
        )
        val now = ZonedDateTime.of(2026, 8, 1, 0, 0, 0, 0, zone)
        val fire = FireInstantEngine.computeNextFire(input, zone, now, settings)
        assertEquals(FireInstantEngine.FireReason.REMINDER_LEAD, fire?.reason)
        assertEquals(ZonedDateTime.of(2026, 8, 6, 14, 0, 0, 0, zone), fire?.at) // P14D before Aug 20
        assertEquals(ZonedDateTime.of(2026, 8, 20, 14, 0, 0, 0, zone), fire?.occurrenceAnchor)
    }

    @Test
    fun `once all leads have passed, falls back to the due occurrence itself`() {
        val input = TaskFireInput(
            taskId = "t1",
            schedule = TaskSchedule(type = TaskType.EVENT, dueLocalDate = LocalDate.of(2026, 8, 20), dueLocalTime = LocalTime.of(14, 0)),
            createdAt = createdAt,
            reminderOffsets = listOf("P14D", "P1D")
        )
        val now = ZonedDateTime.of(2026, 8, 19, 15, 0, 0, 0, zone) // after both leads, before due
        val fire = FireInstantEngine.computeNextFire(input, zone, now, settings)
        assertEquals(FireInstantEngine.FireReason.OCCURRENCE, fire?.reason)
        assertEquals(ZonedDateTime.of(2026, 8, 20, 14, 0, 0, 0, zone), fire?.at)
    }

    @Test
    fun `RECURRING and NAG types ignore reminderOffsets entirely`() {
        val input = TaskFireInput(
            taskId = "t1",
            schedule = TaskSchedule(
                type = TaskType.RECURRING,
                recAnchor = RecurrenceAnchor.CALENDAR,
                recFreq = RecurrenceFrequency.DAILY,
                recTimesOfDay = listOf(LocalTime.of(8, 0))
            ),
            createdAt = createdAt,
            reminderOffsets = listOf("P14D") // must be ignored per DATA_MODEL.md
        )
        val now = ZonedDateTime.of(2026, 6, 1, 9, 0, 0, 0, zone)
        val fire = FireInstantEngine.computeNextFire(input, zone, now, settings)
        assertEquals(FireInstantEngine.FireReason.OCCURRENCE, fire?.reason)
        assertEquals(LocalDate.of(2026, 6, 2), fire?.at?.toLocalDate())
    }

    // --- (c): NAG + not_before ---

    @Test
    fun `NAG respects not_before and then nags every interval`() {
        val input = TaskFireInput(
            taskId = "t1",
            schedule = TaskSchedule(type = TaskType.NAG, notBefore = LocalDate.of(2026, 7, 7), nagIntervalHours = 24.0),
            createdAt = createdAt
        )
        val beforeNotBefore = FireInstantEngine.computeNextFire(input, zone, ZonedDateTime.of(2026, 6, 1, 9, 0, 0, 0, zone), settings)
        assertEquals(LocalDate.of(2026, 7, 7), beforeNotBefore?.at?.toLocalDate())
        assertEquals(FireInstantEngine.FireReason.OCCURRENCE, beforeNotBefore?.reason)

        val afterFirstNag = FireInstantEngine.computeNextFire(
            input, zone, ZonedDateTime.of(2026, 7, 7, 9, 0, 0, 0, zone).plusHours(30), settings
        )
        assertEquals(ZonedDateTime.of(2026, 7, 9, 9, 0, 0, 0, zone), afterFirstNag?.at)
    }

    // --- (e): DEADLINE escalation layered with occurrence + leads ---

    @Test
    fun `DEADLINE combines occurrence, leads and escalation, picking the earliest`() {
        val input = TaskFireInput(
            taskId = "t1",
            schedule = TaskSchedule(type = TaskType.DEADLINE, dueLocalDate = LocalDate.of(2026, 8, 15)),
            createdAt = createdAt,
            reminderOffsets = listOf("P14D", "P7D", "P1D")
        )
        // 2 days out: escalation (09:00/14:00/19:00) is earlier in the day than anything else pending.
        val now = ZonedDateTime.of(2026, 8, 13, 0, 0, 0, 0, zone)
        val fire = FireInstantEngine.computeNextFire(input, zone, now, settings)
        assertEquals(FireInstantEngine.FireReason.ESCALATION, fire?.reason)
        assertEquals(ZonedDateTime.of(2026, 8, 13, 9, 0, 0, 0, zone), fire?.at)
    }

    // --- (f)/(g): snooze + quiet hours ---

    @Test
    fun `snooze suppresses everything and fires exactly at snoozedUntil`() {
        val input = TaskFireInput(
            taskId = "t1",
            schedule = TaskSchedule(type = TaskType.NAG, nagIntervalHours = 1.0),
            createdAt = createdAt,
            snoozedUntil = ZonedDateTime.of(2026, 6, 1, 15, 0, 0, 0, zone)
        )
        val now = ZonedDateTime.of(2026, 6, 1, 9, 0, 0, 0, zone)
        val fire = FireInstantEngine.computeNextFire(input, zone, now, settings)
        assertEquals(FireInstantEngine.FireReason.SNOOZE, fire?.reason)
        assertEquals(ZonedDateTime.of(2026, 6, 1, 15, 0, 0, 0, zone), fire?.at)
    }

    @Test
    fun `snooze landing inside quiet hours defers to quiet-hours end`() {
        val input = TaskFireInput(
            taskId = "t1",
            schedule = TaskSchedule(type = TaskType.EVENT, dueLocalDate = LocalDate.of(2026, 8, 1)),
            createdAt = createdAt,
            snoozedUntil = ZonedDateTime.of(2026, 6, 1, 23, 15, 0, 0, zone) // +1h snooze from 22:15, lands in quiet hours
        )
        val now = ZonedDateTime.of(2026, 6, 1, 22, 15, 0, 0, zone)
        val fire = FireInstantEngine.computeNextFire(input, zone, now, settings)
        assertEquals(FireInstantEngine.FireReason.SNOOZE, fire?.reason)
        assertEquals(ZonedDateTime.of(2026, 6, 2, 8, 0, 0, 0, zone), fire?.at)
        // The anchor (what's being acted on) stays the true snooze instant, not the deferred one.
        assertEquals(ZonedDateTime.of(2026, 6, 1, 23, 15, 0, 0, zone), fire?.occurrenceAnchor)
    }

    @Test
    fun `a past snoozedUntil is inert, falling back to the normal schedule`() {
        val input = TaskFireInput(
            taskId = "t1",
            schedule = TaskSchedule(type = TaskType.NAG, nagIntervalHours = 24.0),
            createdAt = createdAt,
            snoozedUntil = ZonedDateTime.of(2026, 5, 1, 9, 0, 0, 0, zone) // already in the past
        )
        val now = ZonedDateTime.of(2026, 6, 1, 9, 0, 0, 0, zone)
        val fire = FireInstantEngine.computeNextFire(input, zone, now, settings)
        assertEquals(FireInstantEngine.FireReason.OCCURRENCE, fire?.reason)
    }

    @Test
    fun `EVENT due exactly at quiet-hours start defers to quiet-hours end`() {
        val input = TaskFireInput(
            taskId = "t1",
            schedule = TaskSchedule(type = TaskType.EVENT, dueLocalDate = LocalDate.of(2026, 8, 20), dueLocalTime = LocalTime.of(22, 0)),
            createdAt = createdAt
        )
        val now = ZonedDateTime.of(2026, 8, 1, 0, 0, 0, 0, zone)
        val fire = FireInstantEngine.computeNextFire(input, zone, now, settings)
        assertEquals(ZonedDateTime.of(2026, 8, 21, 8, 0, 0, 0, zone), fire?.at)
    }

    @Test
    fun `EVENT due exactly at quiet-hours end fires normally, not deferred`() {
        val input = TaskFireInput(
            taskId = "t1",
            schedule = TaskSchedule(type = TaskType.EVENT, dueLocalDate = LocalDate.of(2026, 8, 20), dueLocalTime = LocalTime.of(8, 0)),
            createdAt = createdAt
        )
        val now = ZonedDateTime.of(2026, 8, 1, 0, 0, 0, 0, zone)
        val fire = FireInstantEngine.computeNextFire(input, zone, now, settings)
        assertEquals(ZonedDateTime.of(2026, 8, 20, 8, 0, 0, 0, zone), fire?.at)
    }

    // --- (h): exclusions ---

    @Test
    fun `SOMEDAY never produces a fire`() {
        val input = TaskFireInput(taskId = "t1", schedule = TaskSchedule(type = TaskType.SOMEDAY), createdAt = createdAt)
        assertNull(FireInstantEngine.computeNextFire(input, zone, ZonedDateTime.now(zone), settings))
    }

    // --- Bulgaria DST, both directions (via the new date-based reminder-offset arithmetic) ---
    // Quiet hours disabled here: both transitions land around 03:00-04:00, which the default
    // 22:00-08:00 quiet window would otherwise defer to 08:00, masking the DST arithmetic under test
    // (that deferral behavior has its own coverage in QuietHoursTest / the quiet-hours-boundary tests above).
    private val noQuietHours = settings.copy(quietHoursStart = LocalTime.MIDNIGHT, quietHoursEnd = LocalTime.MIDNIGHT)

    private fun findTransition(yearStartInstant: ZonedDateTime, isGap: Boolean): ZoneOffsetTransition {
        var instant = yearStartInstant.toInstant()
        repeat(10) {
            val transition = zone.rules.nextTransition(instant) ?: error("no more transitions found")
            if (transition.isGap == isGap) return transition
            instant = transition.instant
        }
        error("could not find a transition of the requested kind within 10 lookups")
    }

    @Test
    fun `P1D reminder lead lands in the spring-forward gap and shifts forward`() {
        val yearStart = ZonedDateTime.of(2026, 1, 1, 0, 0, 0, 0, zone)
        val gap = findTransition(yearStart, isGap = true)
        val transitionDate = gap.dateTimeBefore.toLocalDate()
        assertEquals(Month.MARCH, transitionDate.month)

        val dueDate = transitionDate.plusDays(1)
        val dueTime = gap.dateTimeBefore.toLocalTime().plusMinutes(30) // e.g. 03:30, nonexistent on transitionDate
        val input = TaskFireInput(
            taskId = "t1",
            schedule = TaskSchedule(type = TaskType.EVENT, dueLocalDate = dueDate, dueLocalTime = dueTime),
            createdAt = createdAt,
            reminderOffsets = listOf("P1D")
        )
        val now = ZonedDateTime.of(transitionDate, LocalTime.MIDNIGHT, zone)
        val fire = FireInstantEngine.computeNextFire(input, zone, now, noQuietHours)

        assertEquals(FireInstantEngine.FireReason.REMINDER_LEAD, fire?.reason)
        assertEquals(transitionDate, fire?.at?.toLocalDate())
        assertEquals(dueTime.plus(gap.duration), fire?.at?.toLocalTime())
        assertEquals(gap.offsetAfter, fire?.at?.offset)
    }

    @Test
    fun `P1D reminder lead lands in the fall-back overlap and resolves to the first occurrence`() {
        val yearStart = ZonedDateTime.of(2026, 1, 1, 0, 0, 0, 0, zone)
        val overlap = findTransition(yearStart, isGap = false)
        val transitionDate = overlap.dateTimeAfter.toLocalDate()
        assertEquals(Month.OCTOBER, transitionDate.month)

        val dueDate = transitionDate.plusDays(1)
        val dueTime = overlap.dateTimeAfter.toLocalTime().plusMinutes(30) // e.g. 03:30, ambiguous on transitionDate
        val input = TaskFireInput(
            taskId = "t1",
            schedule = TaskSchedule(type = TaskType.EVENT, dueLocalDate = dueDate, dueLocalTime = dueTime),
            createdAt = createdAt,
            reminderOffsets = listOf("P1D")
        )
        val now = ZonedDateTime.of(transitionDate, LocalTime.MIDNIGHT, zone)
        val fire = FireInstantEngine.computeNextFire(input, zone, now, noQuietHours)

        assertEquals(FireInstantEngine.FireReason.REMINDER_LEAD, fire?.reason)
        assertEquals(transitionDate, fire?.at?.toLocalDate())
        assertEquals(dueTime, fire?.at?.toLocalTime())
        assertEquals(overlap.offsetBefore, fire?.at?.offset)
    }

    // --- globalNext ---

    @Test
    fun `globalNext returns the minimum instant and every task tied at it`() {
        val sameInstant = LocalDate.of(2026, 8, 20) to LocalTime.of(14, 0)
        val inputs = listOf(
            TaskFireInput("a", TaskSchedule(type = TaskType.EVENT, dueLocalDate = sameInstant.first, dueLocalTime = sameInstant.second), createdAt),
            TaskFireInput("b", TaskSchedule(type = TaskType.EVENT, dueLocalDate = sameInstant.first, dueLocalTime = sameInstant.second), createdAt),
            TaskFireInput("c", TaskSchedule(type = TaskType.EVENT, dueLocalDate = LocalDate.of(2026, 9, 1)), createdAt)
        )
        val now = ZonedDateTime.of(2026, 6, 1, 0, 0, 0, 0, zone)
        val (at, due) = FireInstantEngine.globalNext(inputs, zone, now, settings)!!

        assertEquals(ZonedDateTime.of(2026, 8, 20, 14, 0, 0, 0, zone), at)
        assertEquals(setOf("a", "b"), due.map { it.taskId }.toSet())
        assertTrue(due.all { it.reason == FireInstantEngine.FireReason.OCCURRENCE })
    }

    @Test
    fun `globalNext ignores SOMEDAY tasks and returns null when nothing is scheduled`() {
        val inputs = listOf(TaskFireInput("a", TaskSchedule(type = TaskType.SOMEDAY), createdAt))
        assertNull(FireInstantEngine.globalNext(inputs, zone, ZonedDateTime.now(zone), settings))
    }

    // --- missedSince: generic stepping oracle, incl. a CONSUMABLE daily-follow-up chain ---

    @Test
    fun `missedSince finds nothing when nothing fired in the window`() {
        val input = TaskFireInput("a", TaskSchedule(type = TaskType.EVENT, dueLocalDate = LocalDate.of(2026, 9, 1)), createdAt)
        val watermark = ZonedDateTime.of(2026, 6, 1, 0, 0, 0, 0, zone)
        val now = ZonedDateTime.of(2026, 6, 2, 0, 0, 0, 0, zone)
        assertTrue(FireInstantEngine.missedSince(listOf(input), zone, watermark, now, settings).isEmpty())
    }

    @Test
    fun `missedSince recovers a CONSUMABLE daily follow-up chain missed over several days`() {
        // Reminder date is June 11 09:00; stock hasn't been updated since, and the watermark
        // predates it while `now` is 3 days after -> 4 daily follow-ups should have fired.
        val input = TaskFireInput(
            taskId = "meds",
            schedule = TaskSchedule(
                type = TaskType.CONSUMABLE,
                stockQty = 30.0,
                dosePerIntake = 1.0,
                restockLeadDays = 5,
                recTimesOfDay = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)),
                stockRecordedAt = LocalDate.of(2026, 6, 1)
            ),
            createdAt = createdAt
        )
        val watermark = ZonedDateTime.of(2026, 6, 10, 0, 0, 0, 0, zone)
        val now = ZonedDateTime.of(2026, 6, 14, 10, 0, 0, 0, zone)

        val missed = FireInstantEngine.missedSince(listOf(input), zone, watermark, now, settings)

        assertEquals(4, missed.size) // June 11, 12, 13, 14 (09:00 each)
        assertEquals(
            listOf(
                LocalDate.of(2026, 6, 11), LocalDate.of(2026, 6, 12),
                LocalDate.of(2026, 6, 13), LocalDate.of(2026, 6, 14)
            ),
            missed.map { it.at.toLocalDate() }
        )
        assertTrue(missed.all { it.taskId == "meds" && it.reason == FireInstantEngine.FireReason.OCCURRENCE })
    }

    @Test
    fun `missedSince recovers a missed snooze that expired while the device was off`() {
        val input = TaskFireInput(
            taskId = "t1",
            schedule = TaskSchedule(type = TaskType.NAG, nagIntervalHours = 24.0),
            createdAt = createdAt,
            snoozedUntil = ZonedDateTime.of(2026, 6, 12, 15, 0, 0, 0, zone)
        )
        val watermark = ZonedDateTime.of(2026, 6, 10, 0, 0, 0, 0, zone)
        // Same-day window after the snooze fires: narrow enough that the NAG's own next 24h
        // cadence slot (the following day) isn't also swept up as a second missed fire.
        val now = ZonedDateTime.of(2026, 6, 12, 20, 0, 0, 0, zone)

        val missed = FireInstantEngine.missedSince(listOf(input), zone, watermark, now, settings)
        assertEquals(FireInstantEngine.FireReason.SNOOZE, missed.single().reason)
        assertEquals(ZonedDateTime.of(2026, 6, 12, 15, 0, 0, 0, zone), missed.single().at)
    }
}
