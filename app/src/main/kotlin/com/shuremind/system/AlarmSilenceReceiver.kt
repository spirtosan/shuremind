package com.shuremind.system

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.shuremind.ShuRemindApplication

/**
 * D-42: fires ~5 minutes after an alarm-mode notification posts (scheduled by
 * [NotificationCenter]'s postAlarmNotification), stopping the insistent looping sound while
 * leaving the notification visible. A sibling one-shot timer, independent of the D-07
 * single-next-alarm slot — [NotificationActionReceiver] cancels it early if the user acts first.
 */
class AlarmSilenceReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
        if (notificationId == -1) return
        val title = intent.getStringExtra(EXTRA_TITLE) ?: return
        val contentText = intent.getStringExtra(EXTRA_CONTENT_TEXT) ?: return

        val container = (context.applicationContext as ShuRemindApplication).container
        container.notificationCenter.silenceAlarmNotification(notificationId, title, contentText)
    }

    companion object {
        const val EXTRA_NOTIFICATION_ID = "notification_id"
        const val EXTRA_TITLE = "title"
        const val EXTRA_CONTENT_TEXT = "content_text"
    }
}

/** Shared by [NotificationCenter] (schedule) and [NotificationActionReceiver] (early-cancel) so both build an equal PendingIntent. */
fun alarmSilencePendingIntent(context: Context, notificationId: Int, title: String? = null, contentText: String? = null): PendingIntent {
    val intent = Intent(context, AlarmSilenceReceiver::class.java).apply {
        putExtra(AlarmSilenceReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        title?.let { putExtra(AlarmSilenceReceiver.EXTRA_TITLE, it) }
        contentText?.let { putExtra(AlarmSilenceReceiver.EXTRA_CONTENT_TEXT, it) }
    }
    return PendingIntent.getBroadcast(context, notificationId, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
}
