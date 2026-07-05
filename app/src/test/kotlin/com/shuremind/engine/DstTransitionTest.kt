package com.shuremind.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.Month
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.zone.ZoneOffsetTransition

/**
 * D-06 / DECISIONS.md: all scheduling math is local wall-clock, DST-safe. Bulgaria (Europe/Sofia)
 * springs forward on the last Sunday of March (a nonexistent 03:00-04:00 local gap) and falls back
 * on the last Sunday of October (an ambiguous 03:00-04:00 local overlap). These transitions are
 * looked up dynamically from the zone's own rules rather than hardcoded, so the test stays correct
 * for any year.
 */
class DstTransitionTest {

    private val zone: ZoneId = ZoneId.of("Europe/Sofia")

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
    fun `spring-forward gap falls on the last Sunday of March and shifts forward`() {
        val yearStart = ZonedDateTime.of(2026, 1, 1, 0, 0, 0, 0, zone)
        val gap = findTransition(yearStart, isGap = true)

        // Confirm this is indeed the last-Sunday-of-March transition.
        val transitionDate = gap.dateTimeBefore.toLocalDate()
        assertEquals(Month.MARCH, transitionDate.month)
        assertEquals(DayOfWeek.SUNDAY, transitionDate.dayOfWeek)
        assertTrue(transitionDate.plusDays(7).month != Month.MARCH) // last Sunday: +7 days rolls into April

        // A local time inside the gap (e.g. 03:30, which never occurs) shifts forward by the gap length.
        val requested: LocalDateTime = gap.dateTimeBefore.plusMinutes(30)
        val result = FixedDateEngine.toZonedDateTime(requested.toLocalDate(), requested.toLocalTime(), zone)

        assertEquals(requested.plus(gap.duration), result.toLocalDateTime())
        assertEquals(gap.offsetAfter, result.offset)
    }

    @Test
    fun `fall-back overlap falls on the last Sunday of October and resolves to the first occurrence`() {
        val yearStart = ZonedDateTime.of(2026, 1, 1, 0, 0, 0, 0, zone)
        val overlap = findTransition(yearStart, isGap = false)

        val transitionDate = overlap.dateTimeAfter.toLocalDate()
        assertEquals(Month.OCTOBER, transitionDate.month)
        assertEquals(DayOfWeek.SUNDAY, transitionDate.dayOfWeek)
        assertTrue(transitionDate.plusDays(7).month != Month.OCTOBER) // last Sunday: +7 days rolls into November

        // A local time inside the ambiguous overlap (e.g. 03:30, which occurs twice) resolves to
        // the EARLIER offset per D-06 ("ambiguous -> first occurrence").
        val requested: LocalDateTime = overlap.dateTimeAfter.plusMinutes(30)
        val result = FixedDateEngine.toZonedDateTime(requested.toLocalDate(), requested.toLocalTime(), zone)

        assertEquals(requested, result.toLocalDateTime())
        assertEquals(overlap.offsetBefore, result.offset)
    }

    @Test
    fun `a daily recurring reminder rolls onto the transition day itself with the post-transition offset`() {
        val yearStart = ZonedDateTime.of(2026, 1, 1, 0, 0, 0, 0, zone)
        val gap = findTransition(yearStart, isGap = true)
        val transitionDate = gap.dateTimeBefore.toLocalDate()

        val anchor = transitionDate.minusDays(30)
        // Just after the previous day's 09:00 slot, so the next grid hit rolls onto transitionDate.
        val now = ZonedDateTime.of(transitionDate.minusDays(1), java.time.LocalTime.of(10, 0), zone)

        val result = CalendarRecurrenceEngine.nextOccurrence(
            freq = RecurrenceFrequency.DAILY,
            interval = 1,
            daysOfWeek = emptySet(),
            dayOfMonth = null,
            timesOfDay = listOf(java.time.LocalTime.of(9, 0)),
            endDate = null,
            anchorDate = anchor,
            zone = zone,
            now = now
        )
        // 09:00 on the transition day is safely after the 03:00-04:00 gap, so the day-matching
        // arithmetic (calendar-day based, DST-blind) is unaffected — but the resulting instant
        // correctly carries the POST-transition offset (Bulgaria is already on EEST by 09:00).
        assertEquals(transitionDate, result?.toLocalDate())
        assertEquals(java.time.LocalTime.of(9, 0), result?.toLocalTime())
        assertEquals(gap.offsetAfter, result?.offset)
    }
}
