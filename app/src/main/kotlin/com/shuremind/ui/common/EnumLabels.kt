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

fun DayOfWeek.shortLabelRes(): Int = when (this) {
    DayOfWeek.MONDAY -> R.string.day_mo
    DayOfWeek.TUESDAY -> R.string.day_tu
    DayOfWeek.WEDNESDAY -> R.string.day_we
    DayOfWeek.THURSDAY -> R.string.day_th
    DayOfWeek.FRIDAY -> R.string.day_fr
    DayOfWeek.SATURDAY -> R.string.day_sa
    DayOfWeek.SUNDAY -> R.string.day_su
}
