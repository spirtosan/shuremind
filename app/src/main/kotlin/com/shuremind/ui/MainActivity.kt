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
import com.shuremind.ui.meter.MeterReadingsScreen
import com.shuremind.ui.meter.MeterReadingsViewModel
import com.shuremind.ui.review.WeeklyReviewScreen
import com.shuremind.ui.review.WeeklyReviewViewModel
import com.shuremind.ui.settings.SettingsScreen
import com.shuremind.ui.settings.SettingsViewModel
import com.shuremind.ui.theme.ShuRemindTheme
import kotlinx.coroutines.launch

/**
 * Extends AppCompatActivity (not ComponentActivity) per D-18: AppCompatDelegate.setApplicationLocales
 * needs it. singleTop + onNewIntent (rather than a separate trampoline receiver/activity) is how a
 * notification tap opens task detail directly, per STEP 6's "no trampolines, Android 12+ compliant".
 * D-27/D-28 route two more notification-tap targets through the same extras: a CONSUMABLE run-out
 * notification's Restock action (EXTRA_OPEN_RESTOCK) and the seeded weekly-review task's own tap
 * (EXTRA_OPEN_WEEKLY_REVIEW).
 */
class MainActivity : AppCompatActivity() {

    private val container get() = (application as ShuRemindApplication).container
    private val factory by lazy { ViewModelFactory(container) }

    private val mainViewModel: MainViewModel by viewModels { factory }
    private val taskDetailViewModel: TaskDetailViewModel by viewModels { factory }
    private val settingsViewModel: SettingsViewModel by viewModels { factory }
    private val meterReadingsViewModel: MeterReadingsViewModel by viewModels { factory }
    private val weeklyReviewViewModel: WeeklyReviewViewModel by viewModels { factory }

    private var pendingTaskId by mutableStateOf<String?>(null)
    private var pendingOpenRestock by mutableStateOf(false)
    private var pendingOpenWeeklyReview by mutableStateOf(false)

    // STEP 7: POST_NOTIFICATIONS runtime request (API 33+ only). Registered before STARTED, as required.
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* graceful either way, D-17 */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        readPendingExtras(intent)
        requestNotificationPermissionIfNeeded()
        setContent {
            ShuRemindTheme {
                ShuRemindApp(
                    mainViewModel = mainViewModel,
                    taskDetailViewModel = taskDetailViewModel,
                    settingsViewModel = settingsViewModel,
                    meterReadingsViewModel = meterReadingsViewModel,
                    weeklyReviewViewModel = weeklyReviewViewModel,
                    pendingTaskId = pendingTaskId,
                    pendingOpenRestock = pendingOpenRestock,
                    pendingOpenWeeklyReview = pendingOpenWeeklyReview
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readPendingExtras(intent)
    }

    private fun readPendingExtras(intent: Intent?) {
        pendingTaskId = intent?.getStringExtra(EXTRA_TASK_ID)
        pendingOpenRestock = intent?.getBooleanExtra(EXTRA_OPEN_RESTOCK, false) ?: false
        pendingOpenWeeklyReview = intent?.getBooleanExtra(EXTRA_OPEN_WEEKLY_REVIEW, false) ?: false
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
        const val EXTRA_OPEN_RESTOCK = "open_restock"
        const val EXTRA_OPEN_WEEKLY_REVIEW = "open_weekly_review"
    }
}

private sealed interface Screen {
    data object MainList : Screen
    data class TaskDetail(val taskId: String, val openRestock: Boolean = false) : Screen
    data object Settings : Screen
    data object MeterReadings : Screen
    data object WeeklyReview : Screen
}

@Composable
private fun ShuRemindApp(
    mainViewModel: MainViewModel,
    taskDetailViewModel: TaskDetailViewModel,
    settingsViewModel: SettingsViewModel,
    meterReadingsViewModel: MeterReadingsViewModel,
    weeklyReviewViewModel: WeeklyReviewViewModel,
    pendingTaskId: String? = null,
    pendingOpenRestock: Boolean = false,
    pendingOpenWeeklyReview: Boolean = false
) {
    var screen by rememberSaveable(stateSaver = ScreenSaver) {
        mutableStateOf(
            when {
                pendingOpenWeeklyReview -> Screen.WeeklyReview
                pendingTaskId != null -> Screen.TaskDetail(pendingTaskId, pendingOpenRestock)
                else -> Screen.MainList
            }
        )
    }

    // Covers the app-already-running case (singleTop -> onNewIntent): first composition already
    // picked up the initial pending extras above, this reacts to subsequent notification taps.
    LaunchedEffect(pendingTaskId, pendingOpenRestock, pendingOpenWeeklyReview) {
        if (pendingOpenWeeklyReview) {
            screen = Screen.WeeklyReview
        } else if (pendingTaskId != null) {
            screen = Screen.TaskDetail(pendingTaskId, pendingOpenRestock)
        }
    }

    BackHandler(enabled = screen != Screen.MainList) {
        screen = Screen.MainList
    }

    when (val current = screen) {
        is Screen.MainList -> MainScreen(
            viewModel = mainViewModel,
            onOpenSettings = { screen = Screen.Settings },
            onOpenTask = { taskId -> screen = Screen.TaskDetail(taskId) },
            onOpenMeterReadings = { screen = Screen.MeterReadings },
            onOpenWeeklyReview = { screen = Screen.WeeklyReview }
        )
        is Screen.TaskDetail -> TaskDetailScreen(
            taskId = current.taskId,
            viewModel = taskDetailViewModel,
            onBack = { screen = Screen.MainList },
            autoOpenRestock = current.openRestock
        )
        is Screen.Settings -> SettingsScreen(
            viewModel = settingsViewModel,
            onBack = { screen = Screen.MainList }
        )
        is Screen.MeterReadings -> MeterReadingsScreen(
            viewModel = meterReadingsViewModel,
            onBack = { screen = Screen.MainList }
        )
        is Screen.WeeklyReview -> WeeklyReviewScreen(
            viewModel = weeklyReviewViewModel,
            onBack = { screen = Screen.MainList },
            onOpenTask = { taskId -> screen = Screen.TaskDetail(taskId) }
        )
    }
}

private val ScreenSaver = androidx.compose.runtime.saveable.Saver<Screen, String>(
    save = { screen ->
        when (screen) {
            is Screen.MainList -> "main"
            is Screen.Settings -> "settings"
            is Screen.MeterReadings -> "meters"
            is Screen.WeeklyReview -> "review"
            is Screen.TaskDetail -> (if (screen.openRestock) "restock:" else "task:") + screen.taskId
        }
    },
    restore = { value ->
        when {
            value == "main" -> Screen.MainList
            value == "settings" -> Screen.Settings
            value == "meters" -> Screen.MeterReadings
            value == "review" -> Screen.WeeklyReview
            value.startsWith("restock:") -> Screen.TaskDetail(value.removePrefix("restock:"), openRestock = true)
            value.startsWith("task:") -> Screen.TaskDetail(value.removePrefix("task:"))
            else -> Screen.MainList
        }
    }
)
