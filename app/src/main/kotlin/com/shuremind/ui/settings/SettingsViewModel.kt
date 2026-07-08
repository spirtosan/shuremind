package com.shuremind.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shuremind.data.backup.ExportEngine
import com.shuremind.data.backup.ImportCounts
import com.shuremind.data.backup.ImportEngine
import com.shuremind.data.backup.ImportRepository
import com.shuremind.data.backup.BackupRepository
import com.shuremind.data.backup.dto.ExportFile
import com.shuremind.data.backup.entityCounts
import com.shuremind.data.repo.AppLanguage
import com.shuremind.data.repo.BackupFileNaming
import com.shuremind.data.repo.BackupFileWriter
import com.shuremind.data.repo.BackupSettingsRepository
import com.shuremind.data.entity.TagEntity
import com.shuremind.data.repo.LanguageRepository
import com.shuremind.data.repo.SettingsRepository
import com.shuremind.data.repo.TagRepository
import com.shuremind.engine.TaskType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime

data class SettingsUiState(
    val language: AppLanguage = AppLanguage.SYSTEM,
    val quietHoursStart: LocalTime = LocalTime.of(22, 0),
    val quietHoursEnd: LocalTime = LocalTime.of(8, 0),
    val defaultAllDayTime: LocalTime = LocalTime.of(9, 0),
    val currency: String = "EUR",
    val snoozePresetsMinutes: List<Long> = emptyList(),
    val defaultReminderOffsets: Map<TaskType, List<String>> = emptyMap(),
    val defaultSnoozeDurationMinutes: Long = 60L,
    val exactAlarmsOptIn: Boolean = false,
    val autoBackupEnabled: Boolean = false,
    val backupFolderUri: String? = null
)

/** M5 (D-31) typed parse failure, surfaced by the UI as a localized message. */
sealed interface ImportParseError {
    data class UnsupportedSchemaVersion(val found: Int) : ImportParseError
    data object Malformed : ImportParseError
}

/** M5 one-shot feedback events for the Settings screen; the UI clears each after showing it. */
sealed interface SettingsFeedback {
    data object BackupNowSuccess : SettingsFeedback
    data object BackupNowFailure : SettingsFeedback
    data object BackupNoFolder : SettingsFeedback
    data object ExportSuccess : SettingsFeedback
    data object ExportFailure : SettingsFeedback
    data class ImportParseFailed(val error: ImportParseError) : SettingsFeedback
    data object ImportSafetyExportFailed : SettingsFeedback
    data object ImportSuccess : SettingsFeedback
    data object ImportFailure : SettingsFeedback
}

/** D-31: a parsed-but-not-yet-applied import, awaiting the destructive confirmation dialog. */
data class PendingImport(val file: ExportFile, val counts: ImportCounts)

/** Backs the Settings screen (task 7, M2; M5 adds import/export/auto-backup). Language changes apply immediately via AppCompatDelegate. */
class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val languageRepository: LanguageRepository,
    private val backupSettingsRepository: BackupSettingsRepository,
    private val backupFileWriter: BackupFileWriter,
    private val backupRepository: BackupRepository,
    private val importRepository: ImportRepository,
    private val tagRepository: TagRepository,
    private val appVersion: String
) : ViewModel() {

    private val languageState = MutableStateFlow(languageRepository.get())

    val uiState: StateFlow<SettingsUiState> = combine(
        languageState,
        settingsRepository.settings,
        backupSettingsRepository.settings
    ) { language, settings, backupSettings ->
        SettingsUiState(
            language = language,
            quietHoursStart = settings.quietHoursStart,
            quietHoursEnd = settings.quietHoursEnd,
            defaultAllDayTime = settings.defaultAllDayTime,
            currency = settings.currency,
            snoozePresetsMinutes = settings.snoozePresets.map { it.toMinutes() },
            defaultReminderOffsets = settings.defaultReminderOffsets,
            defaultSnoozeDurationMinutes = settings.defaultSnoozeDuration.toMinutes(),
            exactAlarmsOptIn = settings.exactAlarmsOptIn,
            autoBackupEnabled = backupSettings.autoBackupEnabled,
            backupFolderUri = backupSettings.folderUri
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SettingsUiState())

    /** M6 part 1.5 (D-41): backs the Settings "Tags" management dialog. */
    val tags: StateFlow<List<TagEntity>> = tagRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun deleteTag(tagId: String) = viewModelScope.launch {
        tagRepository.deleteTag(tagId)
    }

    private val _feedback = MutableStateFlow<SettingsFeedback?>(null)
    val feedback: StateFlow<SettingsFeedback?> = _feedback

    private val _pendingImport = MutableStateFlow<PendingImport?>(null)
    val pendingImport: StateFlow<PendingImport?> = _pendingImport

    fun consumeFeedback() {
        _feedback.value = null
    }

    fun suggestedExportFileName(): String = BackupFileNaming.fileName(ZonedDateTime.now())

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

    fun setDefaultSnoozeDurationMinutes(minutes: Long) = viewModelScope.launch {
        settingsRepository.setDefaultSnoozeDuration(Duration.ofMinutes(minutes))
    }

    fun setExactAlarmsOptIn(optIn: Boolean) = viewModelScope.launch {
        settingsRepository.setExactAlarmsOptIn(optIn)
    }

    fun setAutoBackupEnabled(enabled: Boolean) = viewModelScope.launch {
        backupSettingsRepository.setAutoBackupEnabled(enabled)
    }

    /**
     * OpenDocumentTree result handler for both entry points (the "Backup folder" row, and the
     * auto-backup toggle's first-enable flow). [enableAfterPick] is true only when the picker was
     * launched because the user flipped the toggle on with no folder set yet — picking a folder
     * from the "Backup folder" row on its own must never change the toggle.
     */
    fun onFolderPicked(uri: String, enableAfterPick: Boolean) = viewModelScope.launch {
        backupSettingsRepository.setFolderUri(uri)
        if (enableAfterPick) {
            backupSettingsRepository.setAutoBackupEnabled(true)
        }
    }

    fun backupNow() = viewModelScope.launch {
        val folderUri = backupSettingsRepository.settings.first().folderUri
        if (folderUri == null) {
            _feedback.value = SettingsFeedback.BackupNoFolder
            return@launch
        }
        val json = buildExportJson()
        val result = backupFileWriter.writeToFolder(folderUri, json)
        _feedback.value = if (result.isSuccess) SettingsFeedback.BackupNowSuccess else SettingsFeedback.BackupNowFailure
    }

    fun exportTo(documentUri: String) = viewModelScope.launch {
        val json = buildExportJson()
        val result = backupFileWriter.writeToDocument(documentUri, json)
        _feedback.value = if (result.isSuccess) SettingsFeedback.ExportSuccess else SettingsFeedback.ExportFailure
    }

    /** Reads and validates [documentUri]; on success, populates [pendingImport] to drive the D-31 confirmation dialog. */
    fun beginImportFrom(documentUri: String) = viewModelScope.launch {
        val text = backupFileWriter.readDocument(documentUri).getOrElse {
            _feedback.value = SettingsFeedback.ImportParseFailed(ImportParseError.Malformed)
            return@launch
        }
        when (val parsed = ImportEngine.parse(text)) {
            is ImportEngine.ParseResult.Success ->
                _pendingImport.value = PendingImport(parsed.file, parsed.file.entityCounts())
            is ImportEngine.ParseResult.UnsupportedSchemaVersion ->
                _feedback.value = SettingsFeedback.ImportParseFailed(ImportParseError.UnsupportedSchemaVersion(parsed.found))
            is ImportEngine.ParseResult.Malformed ->
                _feedback.value = SettingsFeedback.ImportParseFailed(ImportParseError.Malformed)
        }
    }

    fun cancelPendingImport() {
        _pendingImport.value = null
    }

    /** D-31: safety export first (abort on failure), then replace-all import, then recompute+rearm (via the repo's hook). */
    fun confirmImport() = viewModelScope.launch {
        val pending = _pendingImport.value ?: return@launch
        _pendingImport.value = null

        val folderUri = backupSettingsRepository.settings.first().folderUri
        val safetyJson = buildExportJson()
        val safetyResult = backupFileWriter.writeSafetyExport(folderUri, safetyJson)
        if (safetyResult.isFailure) {
            _feedback.value = SettingsFeedback.ImportSafetyExportFailed
            return@launch
        }

        runCatching { importRepository.import(pending.file) }
            .onSuccess { _feedback.value = SettingsFeedback.ImportSuccess }
            .onFailure { _feedback.value = SettingsFeedback.ImportFailure }
    }

    private suspend fun buildExportJson(): String {
        val snapshot = backupRepository.buildSnapshot()
        val file = ExportEngine.buildExportFile(snapshot, appVersion, System.currentTimeMillis())
        return ExportEngine.toJson(file)
    }
}
