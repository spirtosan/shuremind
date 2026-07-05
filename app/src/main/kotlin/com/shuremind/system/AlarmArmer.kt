package com.shuremind.system

/** Testable seam over AlarmManager so RecomputeAndRearm can be unit tested with a fake. */
interface AlarmArmer {

    /** Arms exactly one alarm at [atEpochMillis], replacing whatever was previously armed. */
    fun arm(atEpochMillis: Long, exactAlarmsOptedIn: Boolean)

    fun cancel()
}
