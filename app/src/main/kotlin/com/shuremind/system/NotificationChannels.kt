package com.shuremind.system

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import androidx.core.content.ContextCompat
import com.shuremind.R

/**
 * STEP 6: channels = reminders(HIGH), nag(HIGH), overdue_summary(HIGH). `review`(DEFAULT) is M4
 * (weekly review), not created yet. D-42 adds `alarm`(HIGH): USAGE_ALARM audio attributes + the
 * system's default alarm sound, so an alarm-mode fire plays at alarm volume/stream like a real
 * alarm clock — no DND-bypass request needed, USAGE_ALARM already plays through the alarm channel.
 */
object NotificationChannels {

    const val REMINDERS = "reminders"
    const val NAG = "nag"
    const val OVERDUE_SUMMARY = "overdue_summary"
    const val ALARM = "alarm"

    fun ensureCreated(context: Context) {
        val manager = ContextCompat.getSystemService(context, NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(REMINDERS, context.getString(R.string.channel_reminders_name), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.channel_reminders_description)
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(NAG, context.getString(R.string.channel_nag_name), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.channel_nag_description)
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(OVERDUE_SUMMARY, context.getString(R.string.channel_overdue_summary_name), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.channel_overdue_summary_description)
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(ALARM, context.getString(R.string.channel_alarm_name), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.channel_alarm_description)
                val alarmAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                setSound(RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM), alarmAttributes)
                enableVibration(true)
            }
        )
    }
}
