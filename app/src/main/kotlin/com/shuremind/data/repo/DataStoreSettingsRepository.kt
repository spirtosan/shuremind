package com.shuremind.data.repo

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.shuremind.engine.EngineTuning
import com.shuremind.engine.TaskType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Duration
import java.time.LocalTime

internal class DataStoreSettingsRepository(
    private val dataStore: DataStore<Preferences>,
    private val scheduleChangeNotifier: ScheduleChangeNotifier = ScheduleChangeNotifier.NONE
) : SettingsRepository {

    override val settings: Flow<AppSettings> = dataStore.data.map { prefs ->
        AppSettings(
            quietHoursStart = prefs[QUIET_HOURS_START]?.let(LocalTime::parse) ?: EngineTuning.DEFAULT_QUIET_HOURS_START,
            quietHoursEnd = prefs[QUIET_HOURS_END]?.let(LocalTime::parse) ?: EngineTuning.DEFAULT_QUIET_HOURS_END,
            defaultAllDayTime = prefs[DEFAULT_ALL_DAY_TIME]?.let(LocalTime::parse) ?: EngineTuning.DEFAULT_ALL_DAY_TIME,
            currency = prefs[CURRENCY] ?: "EUR",
            snoozePresets = prefs[SNOOZE_PRESETS]?.let(::parseDurations) ?: EngineTuning.SNOOZE_PRESETS,
            defaultReminderOffsets = AppSettings.DEFAULT_REMINDER_OFFSETS.keys.associateWith { type ->
                prefs[reminderOffsetsKey(type)]?.split(",")?.filter { it.isNotBlank() }
                    ?: AppSettings.DEFAULT_REMINDER_OFFSETS[type].orEmpty()
            },
            defaultSnoozeDuration = prefs[DEFAULT_SNOOZE_DURATION_MINUTES]?.let { Duration.ofMinutes(it.toLong()) }
                ?: AppSettings().defaultSnoozeDuration,
            exactAlarmsOptIn = prefs[EXACT_ALARMS_OPT_IN] ?: false
        )
    }

    override suspend fun setQuietHours(start: LocalTime, end: LocalTime) {
        dataStore.edit { it[QUIET_HOURS_START] = start.toString(); it[QUIET_HOURS_END] = end.toString() }
        scheduleChangeNotifier.onScheduleChanged()
    }

    override suspend fun setDefaultAllDayTime(time: LocalTime) {
        dataStore.edit { it[DEFAULT_ALL_DAY_TIME] = time.toString() }
    }

    override suspend fun setCurrency(code: String) {
        dataStore.edit { it[CURRENCY] = code }
    }

    override suspend fun setSnoozePresets(presets: List<Duration>) {
        dataStore.edit { it[SNOOZE_PRESETS] = presets.joinToString(",") { d -> d.toMinutes().toString() } }
    }

    override suspend fun setDefaultReminderOffsets(type: TaskType, offsets: List<String>) {
        dataStore.edit { it[reminderOffsetsKey(type)] = offsets.joinToString(",") }
    }

    override suspend fun setDefaultSnoozeDuration(duration: Duration) {
        dataStore.edit { it[DEFAULT_SNOOZE_DURATION_MINUTES] = duration.toMinutes().toString() }
    }

    override suspend fun setExactAlarmsOptIn(optIn: Boolean) {
        dataStore.edit { it[EXACT_ALARMS_OPT_IN] = optIn }
        scheduleChangeNotifier.onScheduleChanged()
    }

    private companion object {
        val QUIET_HOURS_START = stringPreferencesKey("quiet_hours_start")
        val QUIET_HOURS_END = stringPreferencesKey("quiet_hours_end")
        val DEFAULT_ALL_DAY_TIME = stringPreferencesKey("default_all_day_time")
        val CURRENCY = stringPreferencesKey("currency")
        val SNOOZE_PRESETS = stringPreferencesKey("snooze_presets_minutes")
        val DEFAULT_SNOOZE_DURATION_MINUTES = stringPreferencesKey("default_snooze_duration_minutes")
        val EXACT_ALARMS_OPT_IN = booleanPreferencesKey("exact_alarms_opt_in")

        fun reminderOffsetsKey(type: TaskType) = stringPreferencesKey("default_reminder_offsets_${type.name}")

        fun parseDurations(csv: String): List<Duration> =
            csv.split(",").filter { it.isNotBlank() }.map { Duration.ofMinutes(it.toLong()) }
    }
}
