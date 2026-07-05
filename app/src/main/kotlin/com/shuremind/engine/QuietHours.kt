package com.shuremind.engine

import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Global quiet hours (D-12): any fire instant that lands inside the quiet window defers to the
 * window's end. Window is half-open [start, end) so a fire exactly at quiet-end is NOT deferred
 * (allowed to fire normally) while a fire exactly at quiet-start IS deferred.
 */
object QuietHours {

    fun deferIfInside(instant: ZonedDateTime, start: LocalTime, end: LocalTime, zone: ZoneId): ZonedDateTime {
        if (start == end) return instant // zero-width window = quiet hours effectively disabled

        val time = instant.toLocalTime()
        val wraps = start.isAfter(end)
        val insideQuiet = if (wraps) {
            !time.isBefore(start) || time.isBefore(end)
        } else {
            !time.isBefore(start) && time.isBefore(end)
        }
        if (!insideQuiet) return instant

        var quietEnd = FixedDateEngine.toZonedDateTime(instant.toLocalDate(), end, zone)
        val endIsNextDay = if (wraps) !time.isBefore(start) else quietEnd.isBefore(instant)
        if (endIsNextDay) quietEnd = FixedDateEngine.toZonedDateTime(instant.toLocalDate().plusDays(1), end, zone)
        return quietEnd
    }
}
