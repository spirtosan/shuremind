package com.shuremind.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.shuremind.ShuRemindApplication
import com.shuremind.di.ViewModelFactory
import com.shuremind.ui.detail.TaskDetailScreen
import com.shuremind.ui.detail.TaskDetailViewModel
import com.shuremind.ui.main.MainScreen
import com.shuremind.ui.main.MainViewModel
import com.shuremind.ui.settings.SettingsScreen
import com.shuremind.ui.settings.SettingsViewModel
import com.shuremind.ui.theme.ShuRemindTheme
import kotlinx.coroutines.launch

/**
 * Extends AppCompatActivity (not ComponentActivity) per D-18: AppCompatDelegate.setApplicationLocales
 * needs it. singleTop + onNewIntent (rather than a separate trampoline receiver/activity) is how a
 * notification tap opens task detail directly, per STEP 6's "no trampolines, Android 12+ compliant".
 */
class MainActivity : AppCompatActivity() {

    private val container get() = (application as ShuRemindApplication).container
    private val factory by lazy { ViewModelFactory(container) }

    private val mainViewModel: MainViewModel by viewModels { factory }
    private val taskDetailViewModel: TaskDetailViewModel by viewModels { factory }
    private val settingsViewModel: SettingsViewModel by viewModels { factory }

    private var pendingTaskId by mutableStateOf<String?>(null)

    // STEP 7: POST_NOTIFICATIONS runtime request (API 33+ only). Registered before STARTED, as required.
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* graceful either way, D-17 */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingTaskId = intent?.getStringExtra(EXTRA_TASK_ID)
        requestNotificationPermissionIfNeeded()
        setContent {
            ShuRemindTheme {
                ShuRemindApp(
                    mainViewModel = mainViewModel,
                    taskDetailViewModel = taskDetailViewModel,
                    settingsViewModel = settingsViewModel,
                    pendingTaskId = pendingTaskId
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingTaskId = intent.getStringExtra(EXTRA_TASK_ID)
    }

    // CLAUDE.md M3: recompute/re-arm on app open, not just alarm fire/boot/housekeeping.
    override fun onStart() {
        super.onStart()
        lifecycleScope.launch { container.recomputeAndRearm.run() }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    companion object {
        const val EXTRA_TASK_ID = "task_id"
    }
}

private sealed interface Screen {
    data object MainList : Screen
    data class TaskDetail(val taskId: String) : Screen
    data object Settings : Screen
}

@Composable
private fun ShuRemindApp(
    mainViewModel: MainViewModel,
    taskDetailViewModel: TaskDetailViewModel,
    settingsViewModel: SettingsViewModel,
    pendingTaskId: String? = null
) {
    var screen by rememberSaveable(stateSaver = ScreenSaver) {
        mutableStateOf(pendingTaskId?.let { Screen.TaskDetail(it) } ?: Screen.MainList)
    }

    // Covers the app-already-running case (singleTop -> onNewIntent): first composition already
    // picked up the initial pendingTaskId above, this reacts to subsequent notification taps.
    LaunchedEffect(pendingTaskId) {
        if (pendingTaskId != null) {
            screen = Screen.TaskDetail(pendingTaskId)
        }
    }

    BackHandler(enabled = screen != Screen.MainList) {
        screen = Screen.MainList
    }

    when (val current = screen) {
        is Screen.MainList -> MainScreen(
            viewModel = mainViewModel,
            onOpenSettings = { screen = Screen.Settings },
            onOpenTask = { taskId -> screen = Screen.TaskDetail(taskId) }
        )
        is Screen.TaskDetail -> TaskDetailScreen(
            taskId = current.taskId,
            viewModel = taskDetailViewModel,
            onBack = { screen = Screen.MainList }
        )
        is Screen.Settings -> SettingsScreen(
            viewModel = settingsViewModel,
            onBack = { screen = Screen.MainList }
        )
    }
}

private val ScreenSaver = androidx.compose.runtime.saveable.Saver<Screen, String>(
    save = { screen ->
        when (screen) {
            is Screen.MainList -> "main"
            is Screen.Settings -> "settings"
            is Screen.TaskDetail -> "task:${screen.taskId}"
        }
    },
    restore = { value ->
        when {
            value == "main" -> Screen.MainList
            value == "settings" -> Screen.Settings
            value.startsWith("task:") -> Screen.TaskDetail(value.removePrefix("task:"))
            else -> Screen.MainList
        }
    }
)
