package com.shuremind.engine

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** NAG: repeats every N hours until done, optionally silent before a not_before date. */
object NagEngine {

    /**
     * Next repeat strictly after [now], stepping every [intervalHours] from [anchor],
     * never before [notBefore] (if set).
     */
    fun nextOccurrence(
        intervalHours: Double,
        anchor: ZonedDateTime,
        notBefore: LocalDate?,
        zone: ZoneId,
        now: ZonedDateTime
    ): ZonedDateTime {
        require(intervalHours > 0) { "intervalHours must be positive" }

        val notBeforeInstant = notBefore?.let { LocalDateTime.of(it, EngineTuning.DEFAULT_ALL_DAY_TIME).atZone(zone) }
        val effectiveStart = if (notBeforeInstant != null && notBeforeInstant.isAfter(anchor)) notBeforeInstant else anchor

        if (!now.isAfter(effectiveStart)) return effectiveStart

        val stepMinutes = (intervalHours * 60).toLong()
        val elapsedMinutes = Duration.between(effectiveStart, now).toMinutes()
        val stepsElapsed = elapsedMinutes / stepMinutes
        var candidate = effectiveStart.plusMinutes(stepsElapsed * stepMinutes)
        while (!candidate.isAfter(now)) {
            candidate = candidate.plusMinutes(stepMinutes)
        }
        return candidate
    }
}
