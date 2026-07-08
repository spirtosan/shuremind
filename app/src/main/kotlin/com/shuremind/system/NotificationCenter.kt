package com.shuremind.system

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.shuremind.R
import com.shuremind.data.entity.TaskEntity
import com.shuremind.data.repo.RECURRING_TYPES
import com.shuremind.data.repo.WeeklyReviewSeedRepository
import com.shuremind.engine.FireInstantEngine.GlobalFire
import com.shuremind.engine.TaskType
import com.shuremind.ui.AlarmRingActivity
import com.shuremind.ui.MainActivity
import com.shuremind.ui.common.formatEpochMillis
import java.time.ZoneId

/**
 * Real STEP 6 implementation of [OccurrenceNotifier] and [OverdueSummaryNotifier], backed by
 * NotificationCompat/NotificationManagerCompat. Both are permission-guarded: on API 33+ without
 * POST_NOTIFICATIONS granted, posting is a silent no-op — the app keeps working, just quieter,
 * per the graceful-degradation philosophy in D-17 (permission onboarding covers asking for it).
 */
class NotificationCenter(
    private val context: Context,
    private val weeklyReviewSeedRepository: WeeklyReviewSeedRepository,
    private val zone: ZoneId = ZoneId.systemDefault()
) : OccurrenceNotifier, OverdueSummaryNotifier {

    // hasNotificationPermission() below performs the real check; suppressed because lint can't see
    // through the helper method call to recognize it as an equivalent guard.
    @SuppressLint("MissingPermission")
    override suspend fun postOccurrenceNotification(task: TaskEntity, fire: GlobalFire) {
        if (!hasNotificationPermission()) return
        if (fire.isAlarm) {
            postAlarmNotification(task, fire)
            return
        }
        val notificationId = occurrenceNotificationId(task.id, fire.occurrenceLocal)
        val channel = if (task.type == TaskType.NAG) NotificationChannels.NAG else NotificationChannels.REMINDERS
        val locale = context.resources.configuration.locales[0]
        // D-28: the seeded weekly-review task's own notification tap opens the review screen, not
        // its (otherwise unremarkable) task detail — recognized by stored task id, not by type/title.
        val isWeeklyReviewTask = weeklyReviewSeedRepository.seededTaskId() == task.id

        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(task.title)
            .setContentText(formatEpochMillis(fire.at.toInstant().toEpochMilli(), zone, locale))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(
                if (isWeeklyReviewTask) weeklyReviewPendingIntent(notificationId) else taskDetailPendingIntent(task.id, notificationId)
            )
            .addAction(actionCompat(R.string.action_done, NotificationActionReceiver.ACTION_DONE, task.id, fire.occurrenceLocal, notificationId, 0))

        if (task.type in RECURRING_TYPES) {
            builder.addAction(actionCompat(R.string.action_skip, NotificationActionReceiver.ACTION_SKIP, task.id, fire.occurrenceLocal, notificationId, 1))
        }
        builder.addAction(actionCompat(R.string.action_snooze, NotificationActionReceiver.ACTION_SNOOZE, task.id, fire.occurrenceLocal, notificationId, 2))

        // D-26: CONSUMABLE run-out reminders get a Restock action that opens task detail with the
        // restock dialog pre-opened (singleTop/onNewIntent, no trampoline — same pattern as EXTRA_TASK_ID).
        if (task.type == TaskType.CONSUMABLE) {
            builder.addAction(restockActionCompat(task.id, notificationId))
        }

        NotificationManagerCompat.from(context).notify(notificationId, builder.build())
    }

    /**
     * D-42: alarm-mode occurrence fire — dedicated ALARM channel (USAGE_ALARM, insistent looping
     * sound), full-screen intent to [AlarmRingActivity] (which also becomes the tap target so
     * reopening from the shade shows the same ring screen rather than task detail). Auto-silences
     * after [ALARM_AUTO_SILENCE_MILLIS] via a one-shot AlarmManager timer — a sibling mechanism
     * outside the D-07 single-next-alarm slot, analogous to D-32's sibling BackupWorker.
     */
    @SuppressLint("MissingPermission")
    private fun postAlarmNotification(task: TaskEntity, fire: GlobalFire) {
        val notificationId = occurrenceNotificationId(task.id, fire.occurrenceLocal)
        val locale = context.resources.configuration.locales[0]
        val contentText = formatEpochMillis(fire.at.toInstant().toEpochMilli(), zone, locale)
        val ringIntent = alarmRingPendingIntent(task.id, fire.occurrenceLocal, notificationId, task.title, contentText)

        val notification = NotificationCompat.Builder(context, NotificationChannels.ALARM)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(task.title)
            .setContentText(contentText)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(ringIntent)
            .setFullScreenIntent(ringIntent, true)
            .build()
        notification.flags = notification.flags or Notification.FLAG_INSISTENT

        NotificationManagerCompat.from(context).notify(notificationId, notification)
        scheduleAutoSilence(notificationId, task.title, contentText)
    }

    /** D-42: re-posts the same notification id without the alarm channel/insistent flag — stops the looping sound, keeps the notification visible. */
    // hasNotificationPermission() below performs the real check; suppressed for the same reason as
    // postOccurrenceNotification/postOverdueSummary above (lint can't see through the helper call).
    @SuppressLint("MissingPermission")
    fun silenceAlarmNotification(notificationId: Int, title: String, contentText: String) {
        if (!hasNotificationPermission()) return
        val notification = NotificationCompat.Builder(context, NotificationChannels.REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOnlyAlertOnce(true)
            .build()
        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }

    private fun scheduleAutoSilence(notificationId: Int, title: String, contentText: String) {
        val alarmManager = ContextCompat.getSystemService(context, AlarmManager::class.java) ?: return
        val pendingIntent = alarmSilencePendingIntent(context, notificationId, title, contentText)
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + ALARM_AUTO_SILENCE_MILLIS, pendingIntent)
    }

    private fun alarmRingPendingIntent(taskId: String, occurrenceLocal: String, notificationId: Int, title: String, contentText: String): PendingIntent {
        val intent = Intent(context, AlarmRingActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            .putExtra(AlarmRingActivity.EXTRA_TASK_ID, taskId)
            .putExtra(AlarmRingActivity.EXTRA_OCCURRENCE_LOCAL, occurrenceLocal)
            .putExtra(AlarmRingActivity.EXTRA_NOTIFICATION_ID, notificationId)
            .putExtra(AlarmRingActivity.EXTRA_TITLE, title)
            .putExtra(AlarmRingActivity.EXTRA_CONTENT_TEXT, contentText)
        return PendingIntent.getActivity(context, notificationId, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    @SuppressLint("MissingPermission")
    override fun postOverdueSummary(missedCount: Int) {
        if (!hasNotificationPermission()) return
        val title = context.resources.getQuantityString(R.plurals.overdue_summary_title, missedCount, missedCount)
        val contentIntent = PendingIntent.getActivity(
            context,
            overdueSummaryNotificationId(),
            Intent(context, MainActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, NotificationChannels.OVERDUE_SUMMARY)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()
        NotificationManagerCompat.from(context).notify(overdueSummaryNotificationId(), notification)
    }

    private fun taskDetailPendingIntent(taskId: String, notificationId: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            .putExtra(MainActivity.EXTRA_TASK_ID, taskId)
        return PendingIntent.getActivity(context, notificationId, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun weeklyReviewPendingIntent(notificationId: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            .putExtra(MainActivity.EXTRA_OPEN_WEEKLY_REVIEW, true)
        return PendingIntent.getActivity(context, notificationId, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun restockActionCompat(taskId: String, notificationId: Int): NotificationCompat.Action {
        val intent = Intent(context, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            .putExtra(MainActivity.EXTRA_TASK_ID, taskId)
            .putExtra(MainActivity.EXTRA_OPEN_RESTOCK, true)
        val requestCode = notificationId * 4 + 3
        val pendingIntent = PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Action.Builder(R.drawable.ic_notification, context.getString(R.string.detail_restock_action), pendingIntent).build()
    }

    private fun actionCompat(
        titleRes: Int,
        action: String,
        taskId: String,
        occurrenceLocal: String,
        notificationId: Int,
        actionSlot: Int
    ): NotificationCompat.Action {
        val intent = Intent(context, NotificationActionReceiver::class.java)
            .setAction(action)
            .putExtra(NotificationActionReceiver.EXTRA_TASK_ID, taskId)
            .putExtra(NotificationActionReceiver.EXTRA_OCCURRENCE_LOCAL, occurrenceLocal)
        val requestCode = notificationId * 4 + actionSlot
        val pendingIntent = PendingIntent.getBroadcast(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Action.Builder(R.drawable.ic_notification, context.getString(titleRes), pendingIntent).build()
    }

    private fun hasNotificationPermission(): Boolean {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    private companion object {
        const val ALARM_AUTO_SILENCE_MILLIS = 5 * 60 * 1000L
    }
}
