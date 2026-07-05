package com.shuremind.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.shuremind.ui.detail.TaskDetailViewModel
import com.shuremind.ui.main.MainViewModel
import com.shuremind.ui.settings.SettingsViewModel

/** Injects repositories only — no ViewModel may reference a DAO or the database directly (CLAUDE.md). */
class ViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return when (modelClass) {
            MainViewModel::class.java -> MainViewModel(
                taskRepository = container.taskRepository,
                completionRepository = container.completionRepository,
                tagRepository = container.tagRepository,
                settingsRepository = container.settingsRepository
            ) as T

            TaskDetailViewModel::class.java -> TaskDetailViewModel(
                taskRepository = container.taskRepository,
                tagRepository = container.tagRepository,
                reminderRuleRepository = container.reminderRuleRepository,
                completionRepository = container.completionRepository,
                settingsRepository = container.settingsRepository
            ) as T

            SettingsViewModel::class.java -> SettingsViewModel(
                settingsRepository = container.settingsRepository,
                languageRepository = container.languageRepository
            ) as T

            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
