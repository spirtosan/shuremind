package com.shuremind.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.shuremind.R
import com.shuremind.system.NotificationActionReceiver
import com.shuremind.ui.theme.ShuRemindTheme

/**
 * D-42: minimal full-screen ring UI for an alarm-mode fire, launched via the alarm notification's
 * full-screen intent (shows over the lock screen, turns the screen on). Purely a UI shell — the
 * ringtone/vibration is the posted notification's own insistent alarm-channel sound (STEP 6/D-42 in
 * NotificationCenter), not managed here. Every button routes through the same
 * [NotificationActionReceiver] broadcast the shade notification's own actions would send, so
 * watermark/completion logic has exactly one code path regardless of how the user responded.
 */
class AlarmRingActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        showOverLockScreen()

        val taskId = intent.getStringExtra(EXTRA_TASK_ID)
        val occurrenceLocal = intent.getStringExtra(EXTRA_OCCURRENCE_LOCAL)
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val contentText = intent.getStringExtra(EXTRA_CONTENT_TEXT).orEmpty()

        setContent {
            ShuRemindTheme {
                AlarmRingScreen(
                    title = title,
                    contentText = contentText,
                    onDone = { respond(taskId, occurrenceLocal, NotificationActionReceiver.ACTION_DONE) },
                    onSnooze = { respond(taskId, occurrenceLocal, NotificationActionReceiver.ACTION_SNOOZE) },
                    onDismiss = { respond(taskId, occurrenceLocal, NotificationActionReceiver.ACTION_DISMISS) }
                )
            }
        }
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    /** Sends the exact broadcast a notification action button would send, then finishes — no direct repository/completion calls here (kdoc above). */
    private fun respond(taskId: String?, occurrenceLocal: String?, action: String) {
        if (taskId != null && occurrenceLocal != null) {
            val intent = Intent(this, NotificationActionReceiver::class.java)
                .setAction(action)
                .putExtra(NotificationActionReceiver.EXTRA_TASK_ID, taskId)
                .putExtra(NotificationActionReceiver.EXTRA_OCCURRENCE_LOCAL, occurrenceLocal)
            sendBroadcast(intent)
        }
        finish()
    }

    companion object {
        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_OCCURRENCE_LOCAL = "occurrence_local"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
        const val EXTRA_TITLE = "title"
        const val EXTRA_CONTENT_TEXT = "content_text"
    }
}

@Composable
private fun AlarmRingScreen(
    title: String,
    contentText: String,
    onDone: () -> Unit,
    onSnooze: () -> Unit,
    onDismiss: () -> Unit
) {
    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            Text(
                text = contentText,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 8.dp)
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 48.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.action_done))
                }
                OutlinedButton(onClick = onSnooze, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.action_snooze))
                }
                OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.action_dismiss))
                }
            }
        }
    }
}
