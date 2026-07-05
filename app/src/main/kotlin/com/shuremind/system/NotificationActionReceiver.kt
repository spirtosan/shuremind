package com.shuremind.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.shuremind.ShuRemindApplication
import com.shuremind.engine.CompletionAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Handles the Done/Snooze/Skip broadcast actions on a per-occurrence notification (STEP 6). Snooze
 * applies the single app-wide default snooze duration (D-24) — no duration chooser on the notification.
 */
class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return
        val occurrenceLocal = intent.getStringExtra(EXTRA_OCCURRENCE_LOCAL) ?: return
        val action = intent.action ?: return
        val notificationId = occurrenceNotificationId(taskId, occurrenceLocal)

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
        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_OCCURRENCE_LOCAL = "occurrence_local"
    }
}
