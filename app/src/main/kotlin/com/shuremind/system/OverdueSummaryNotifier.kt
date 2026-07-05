package com.shuremind.system

/** Posts the single grouped "N overdue reminders" notification (D-10/D-23). Implemented in full in M3 STEP 6. */
fun interface OverdueSummaryNotifier {
    fun postOverdueSummary(missedCount: Int)
}
