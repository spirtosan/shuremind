package com.shuremind.system

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * D-07/D-08: exactly one AlarmManager alarm armed at a time, at the globally nearest pending fire
 * instant. Section-E branch (lifted from fshu, per D-17): exact alarms are opt-in and only actually
 * used when the OS also currently permits them; otherwise falls back to inexact-but-idle-tolerant.
 */
class AlarmScheduler(private val context: Context) : AlarmArmer {

    private val alarmManager: AlarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    // canUseExactAlarms() below performs the real canScheduleExactAlarms()/opt-in guard; suppressed
    // because lint can't see through the helper method call to recognize it as an equivalent check.
    @SuppressLint("MissingPermission")
    override fun arm(atEpochMillis: Long, exactAlarmsOptedIn: Boolean) {
        cancel()
        val pendingIntent = alarmPendingIntent()
        if (canUseExactAlarms(exactAlarmsOptedIn)) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atEpochMillis, pendingIntent)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atEpochMillis, pendingIntent)
        }
    }

    override fun cancel() {
        alarmManager.cancel(alarmPendingIntent())
    }

    private fun canUseExactAlarms(exactAlarmsOptedIn: Boolean): Boolean {
        if (!exactAlarmsOptedIn) return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) alarmManager.canScheduleExactAlarms() else true
    }

    private fun alarmPendingIntent(): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private companion object {
        const val REQUEST_CODE = 1001
    }
}
