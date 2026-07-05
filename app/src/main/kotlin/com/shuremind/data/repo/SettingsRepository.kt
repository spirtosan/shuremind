package com.shuremind.data.repo

import com.shuremind.engine.EngineTuning
import com.shuremind.engine.FireSettings
import com.shuremind.engine.TaskType
import kotlinx.coroutines.flow.Flow
import java.time.Duration
import java.time.LocalTime

/** Settings (DataStore, not Room) per DATA_MODEL.md. D-21: app_language lives in AppCompat, not here — see [LanguageRepository]. */
data class AppSettings(
    val quietHoursStart: LocalTime = EngineTuning.DEFAULT_QUIET_HOURS_START,
    val quietHoursEnd: LocalTime = EngineTuning.DEFAULT_QUIET_HOURS_END,
    val defaultAllDayTime: LocalTime = EngineTuning.DEFAULT_ALL_DAY_TIME,
    val currency: String = "EUR",
    val snoozePresets: List<Duration> = EngineTuning.SNOOZE_PRESETS,
    val defaultReminderOffsets: Map<TaskType, List<String>> = DEFAULT_REMINDER_OFFSETS,
    /** D-24: applied by the notification Snooze action (no duration chooser on the notification itself). */
    val defaultSnoozeDuration: Duration = Duration.ofHours(1),
    /** D-08: exact alarms are opt-in; default false (inexact setAndAllowWhileIdle) is acceptable. */
    val exactAlarmsOptIn: Boolean = false
) {
    companion object {
        val DEFAULT_REMINDER_OFFSETS: Map<TaskType, List<String>> = mapOf(
            TaskType.EVENT to emptyList(),
            TaskType.ANNIVERSARY to listOf("P14D", "P1D"),
            TaskType.DEADLINE to listOf("P14D", "P7D", "P1D")
        )
    }
}

/** UI/ViewModels talk only to this repo, never DataStore directly (from M2 on). Fakeable for ViewModel unit tests. */
interface SettingsRepository {

    val settings: Flow<AppSettings>

    suspend fun setQuietHours(start: LocalTime, end: LocalTime)

    suspend fun setDefaultAllDayTime(time: LocalTime)

    suspend fun setCurrency(code: String)

    suspend fun setSnoozePresets(presets: List<Duration>)

    suspend fun setDefaultReminderOffsets(type: TaskType, offsets: List<String>)

    suspend fun setDefaultSnoozeDuration(duration: Duration)

    suspend fun setExactAlarmsOptIn(optIn: Boolean)
}

/** Bridges DataStore-backed settings to the engine's Room/Android-free FireSettings. */
fun AppSettings.toFireSettings(): FireSettings =
    FireSettings(quietHoursStart = quietHoursStart, quietHoursEnd = quietHoursEnd, defaultAllDayTime = defaultAllDayTime)
