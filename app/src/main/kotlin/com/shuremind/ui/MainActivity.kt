package com.shuremind.ui

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.shuremind.ShuRemindApplication
import com.shuremind.di.ViewModelFactory
import com.shuremind.ui.detail.TaskDetailScreen
import com.shuremind.ui.detail.TaskDetailViewModel
import com.shuremind.ui.main.MainScreen
import com.shuremind.ui.main.MainViewModel
import com.shuremind.ui.settings.SettingsScreen
import com.shuremind.ui.settings.SettingsViewModel
import com.shuremind.ui.theme.ShuRemindTheme

/** Extends AppCompatActivity (not ComponentActivity) per D-18: AppCompatDelegate.setApplicationLocales needs it. */
class MainActivity : AppCompatActivity() {

    private val container get() = (application as ShuRemindApplication).container
    private val factory by lazy { ViewModelFactory(container) }

    private val mainViewModel: MainViewModel by viewModels { factory }
    private val taskDetailViewModel: TaskDetailViewModel by viewModels { factory }
    private val settingsViewModel: SettingsViewModel by viewModels { factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ShuRemindTheme {
                ShuRemindApp(
                    mainViewModel = mainViewModel,
                    taskDetailViewModel = taskDetailViewModel,
                    settingsViewModel = settingsViewModel
                )
            }
        }
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
    settingsViewModel: SettingsViewModel
) {
    var screen by rememberSaveable(stateSaver = ScreenSaver) { mutableStateOf<Screen>(Screen.MainList) }

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
