package com.shuremind.data.backup

import com.shuremind.data.backup.dto.ExportSettingsDto
import com.shuremind.data.dao.CompletionLogDao
import com.shuremind.data.dao.MeterReadingDao
import com.shuremind.data.dao.ReminderRuleDao
import com.shuremind.data.dao.TagDao
import com.shuremind.data.dao.TaskDao
import com.shuremind.data.dao.TaskTagDao
import com.shuremind.data.repo.LanguageRepository
import com.shuremind.data.repo.MeterSeedRepository
import com.shuremind.data.repo.SettingsRepository
import com.shuremind.data.repo.WeeklyReviewSeedRepository
import kotlinx.coroutines.flow.first

/** UI/ViewModels talk only to this repo, never the DAOs directly (M2/M3 repo pattern). Builds the D-30 export snapshot. */
interface BackupRepository {
    suspend fun buildSnapshot(): BackupSnapshot
}

internal class RoomBackupRepository(
    private val taskDao: TaskDao,
    private val reminderRuleDao: ReminderRuleDao,
    private val tagDao: TagDao,
    private val taskTagDao: TaskTagDao,
    private val completionLogDao: CompletionLogDao,
    private val meterReadingDao: MeterReadingDao,
    private val settingsRepository: SettingsRepository,
    private val languageRepository: LanguageRepository,
    private val meterSeedRepository: MeterSeedRepository,
    private val weeklyReviewSeedRepository: WeeklyReviewSeedRepository
) : BackupRepository {

    override suspend fun buildSnapshot(): BackupSnapshot {
        val settings = settingsRepository.settings.first()
        val settingsDto = ExportSettingsDto(
            quietHoursStart = settings.quietHoursStart.toString(),
            quietHoursEnd = settings.quietHoursEnd.toString(),
            defaultAllDayTime = settings.defaultAllDayTime.toString(),
            currency = settings.currency,
            defaultReminderOffsets = settings.defaultReminderOffsets.entries.associate { (type, offsets) -> type.name to offsets },
            snoozePresetsMinutes = settings.snoozePresets.map { it.toMinutes() },
            defaultSnoozeDurationMinutes = settings.defaultSnoozeDuration.toMinutes(),
            weeklyReviewTaskId = weeklyReviewSeedRepository.seededTaskId(),
            seededMeterNames = meterSeedRepository.getAllSeeded(),
            uiLanguage = languageRepository.get().tag
        )
        return BackupSnapshot(
            tasks = taskDao.getAllForExport(),
            reminderRules = reminderRuleDao.getAllForExport(),
            tags = tagDao.observeAll().first(),
            taskTags = taskTagDao.observeAll().first(),
            completions = completionLogDao.getAllForExport(),
            meterReadings = meterReadingDao.observeAll().first(),
            settings = settingsDto
        )
    }
}
