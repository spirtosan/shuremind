package com.shuremind.system

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.shuremind.ShuRemindApplication
import com.shuremind.engine.CompletionAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Handles the Done/Snooze/Skip/Dismiss broadcast actions on a per-occurrence notification (STEP 6;
 * D-42 adds Dismiss for the alarm ring screen). Snooze applies the single app-wide default snooze
 * duration (D-24) — no duration chooser on the notification. Dismiss is a no-op besides cancelling:
 * it doesn't mark the task done/skipped/snoozed, so the task's own schedule decides what (if
 * anything) fires next, same as swiping away a normal reminder notification today.
 */
class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return
        val occurrenceLocal = intent.getStringExtra(EXTRA_OCCURRENCE_LOCAL) ?: return
        val action = intent.action ?: return
        val notificationId = occurrenceNotificationId(taskId, occurrenceLocal)

        // D-42: this fire may have been an alarm ring with a pending auto-silence timer — any
        // explicit action (including Dismiss) cancels it so it doesn't resurface a handled/cancelled
        // notification a few minutes later. Harmless no-op if none was ever scheduled.
        ContextCompat.getSystemService(context, AlarmManager::class.java)
            ?.cancel(alarmSilencePendingIntent(context, notificationId))

        val container = (context.applicationContext as ShuRemindApplication).container
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val task = container.taskRepository.getById(taskId)
                if (task != null) {
                    when (action) {
                        ACTION_DONE -> container.completionRepository.completeTask(task, CompletionAction.DONE)
                        ACTION_SKIP -> container.completionRepository.completeTask(task, CompletionAction.SKIPPED)
                        ACTION_SNOOZE -> {
                            val duration = container.settingsRepository.settings.first().defaultSnoozeDuration
                            container.taskRepository.snooze(task, System.currentTimeMillis() + duration.toMillis())
                        }
                        ACTION_DISMISS -> Unit
                    }
                    container.recomputeAndRearm.run()
                }
                NotificationManagerCompat.from(context).cancel(notificationId)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_DONE = "com.shuremind.action.DONE"
        const val ACTION_SKIP = "com.shuremind.action.SKIP"
        const val ACTION_SNOOZE = "com.shuremind.action.SNOOZE"
        const val ACTION_DISMISS = "com.shuremind.action.DISMISS"
        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_OCCURRENCE_LOCAL = "occurrence_local"
    }
}
