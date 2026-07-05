package com.shuremind.system

private const val OVERDUE_SUMMARY_NOTIFICATION_ID = 1

/** Stable per-occurrence id: posting and cancelling (via a notification action) always agree. */
fun occurrenceNotificationId(taskId: String, occurrenceLocal: String): Int =
    "$taskId|$occurrenceLocal".hashCode()

fun overdueSummaryNotificationId(): Int = OVERDUE_SUMMARY_NOTIFICATION_ID
