package com.shuremind.ui.common

import com.shuremind.R
import com.shuremind.engine.RecurrenceAnchor
import com.shuremind.engine.RecurrenceFrequency
import com.shuremind.engine.TaskType
import java.time.DayOfWeek

fun TaskType.labelRes(): Int = when (this) {
    TaskType.EVENT -> R.string.task_type_event
    TaskType.ANNIVERSARY -> R.string.task_type_anniversary
    TaskType.DEADLINE -> R.string.task_type_deadline
    TaskType.WINDOW -> R.string.task_type_window
    TaskType.NAG -> R.string.task_type_nag
    TaskType.RECURRING -> R.string.task_type_recurring
    TaskType.CONSUMABLE -> R.string.task_type_consumable
    TaskType.SOMEDAY -> R.string.task_type_someday
}

/** D-40: one-sentence plain-language explanation shown in the type picker's help dialog. */
fun TaskType.helpRes(): Int = when (this) {
    TaskType.EVENT -> R.string.help_type_event
    TaskType.ANNIVERSARY -> R.string.help_type_anniversary
    TaskType.DEADLINE -> R.string.help_type_deadline
    TaskType.WINDOW -> R.string.help_type_window
    TaskType.NAG -> R.string.help_type_nag
    TaskType.RECURRING -> R.string.help_type_recurring
    TaskType.CONSUMABLE -> R.string.help_type_consumable
    TaskType.SOMEDAY -> R.string.help_type_someday
}

fun RecurrenceFrequency.labelRes(): Int = when (this) {
    RecurrenceFrequency.DAILY -> R.string.rec_freq_daily
    RecurrenceFrequency.WEEKLY -> R.string.rec_freq_weekly
    RecurrenceFrequency.MONTHLY -> R.string.rec_freq_monthly
    RecurrenceFrequency.YEARLY -> R.string.rec_freq_yearly
}

fun RecurrenceAnchor.labelRes(): Int = when (this) {
    RecurrenceAnchor.CALENDAR -> R.string.rec_anchor_calendar
    RecurrenceAnchor.COMPLETION -> R.string.rec_anchor_completion
}

/** D-39: word label shown alongside the 0-3 impact number ("what happens if skipped"). */
fun impactLevelLabelRes(level: Int): Int = when (level) {
    0 -> R.string.impact_level_0
    1 -> R.string.impact_level_1
    2 -> R.string.impact_level_2
    else -> R.string.impact_level_3
}

/** D-39: word label shown alongside the 0-3 urgency number ("how soon it matters"). */
fun urgencyLevelLabelRes(level: Int): Int = when (level) {
    0 -> R.string.urgency_level_0
    1 -> R.string.urgency_level_1
    2 -> R.string.urgency_level_2
    else -> R.string.urgency_level_3
}

fun OffsetUnit.labelRes(): Int = when (this) {
    OffsetUnit.MINUTES -> R.string.reminder_offset_unit_minutes
    OffsetUnit.HOURS -> R.string.reminder_offset_unit_hours
    OffsetUnit.DAYS -> R.string.reminder_offset_unit_days
    OffsetUnit.WEEKS -> R.string.reminder_offset_unit_weeks
}

fun DayOfWeek.shortLabelRes(): Int = when (this) {
    DayOfWeek.MONDAY -> R.string.day_mo
    DayOfWeek.TUESDAY -> R.string.day_tu
    DayOfWeek.WEDNESDAY -> R.string.day_we
    DayOfWeek.THURSDAY -> R.string.day_th
    DayOfWeek.FRIDAY -> R.string.day_fr
    DayOfWeek.SATURDAY -> R.string.day_sa
    DayOfWeek.SUNDAY -> R.string.day_su
}
