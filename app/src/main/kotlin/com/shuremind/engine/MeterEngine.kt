package com.shuremind.engine

import java.time.ZonedDateTime

/** Meter-based maintenance: due when time OR distance-since-last-completion crosses meter_interval. */
object MeterEngine {

    fun isMeterDue(latestMeterValue: Double, lastDoneMeter: Double, meterInterval: Double): Boolean =
        (latestMeterValue - lastDoneMeter) >= meterInterval

    /** Combines a time-based occurrence with the meter check: due when EITHER threshold is crossed. */
    fun isDue(timeOccurrence: ZonedDateTime?, now: ZonedDateTime, meterDue: Boolean): Boolean =
        meterDue || (timeOccurrence != null && !timeOccurrence.isAfter(now))
}
