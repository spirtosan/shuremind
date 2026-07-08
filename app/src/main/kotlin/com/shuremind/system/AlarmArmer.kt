package com.shuremind.system

/** Testable seam over AlarmManager so RecomputeAndRearm can be unit tested with a fake. */
interface AlarmArmer {

    /**
     * Arms exactly one alarm at [atEpochMillis], replacing whatever was previously armed. [isAlarm]
     * (D-42) is true when the globally-nearest instant belongs to an alarm-mode task's occurrence
     * fire — that branch always uses setAlarmClock() (exact, no opt-in needed) regardless of
     * [exactAlarmsOptedIn], which only governs the normal Section-E exact/inexact choice.
     */
    fun arm(atEpochMillis: Long, exactAlarmsOptedIn: Boolean, isAlarm: Boolean = false)

    fun cancel()
}
