package com.shuremind.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shuremind.data.repo.AppLanguage
import com.shuremind.data.repo.LanguageRepository
import com.shuremind.data.repo.SettingsRepository
import com.shuremind.engine.TaskType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalTime

data class SettingsUiState(
    val language: AppLanguage = AppLanguage.SYSTEM,
    val quietHoursStart: LocalTime = LocalTime.of(22, 0),
    val quietHoursEnd: LocalTime = LocalTime.of(8, 0),
    val defaultAllDayTime: LocalTime = LocalTime.of(9, 0),
    val currency: String = "EUR",
    val snoozePresetsMinutes: List<Long> = emptyList(),
    val defaultReminderOffsets: Map<TaskType, List<String>> = emptyMap()
)

/** Backs the Settings screen (task 7, M2). Language changes apply immediately via AppCompatDelegate. */
class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val languageRepository: LanguageRepository
) : ViewModel() {

    private val languageState = MutableStateFlow(languageRepository.get())

    val uiState: StateFlow<SettingsUiState> = combine(languageState, settingsRepository.settings) { language, settings ->
        SettingsUiState(
            language = language,
            quietHoursStart = settings.quietHoursStart,
            quietHoursEnd = settings.quietHoursEnd,
            defaultAllDayTime = settings.defaultAllDayTime,
            currency = settings.currency,
            snoozePresetsMinutes = settings.snoozePresets.map { it.toMinutes() },
            defaultReminderOffsets = settings.defaultReminderOffsets
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SettingsUiState())

    fun setLanguage(language: AppLanguage) {
        languageRepository.set(language)
        languageState.value = language
    }

    fun setQuietHours(start: LocalTime, end: LocalTime) = viewModelScope.launch {
        settingsRepository.setQuietHours(start, end)
    }

    fun setDefaultAllDayTime(time: LocalTime) = viewModelScope.launch {
        settingsRepository.setDefaultAllDayTime(time)
    }

    fun setCurrency(code: String) = viewModelScope.launch {
        settingsRepository.setCurrency(code)
    }

    fun setSnoozePresetsMinutes(minutes: List<Long>) = viewModelScope.launch {
        settingsRepository.setSnoozePresets(minutes.map { Duration.ofMinutes(it) })
    }

    fun setDefaultReminderOffsets(type: TaskType, offsets: List<String>) = viewModelScope.launch {
        settingsRepository.setDefaultReminderOffsets(type, offsets)
    }
}
