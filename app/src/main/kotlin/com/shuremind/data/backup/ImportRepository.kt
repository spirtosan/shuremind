package com.shuremind.data.backup

import com.shuremind.data.backup.dto.ExportFile
import com.shuremind.data.backup.dto.ExportSettingsDto
import com.shuremind.data.dao.CompletionLogDao
import com.shuremind.data.dao.MeterReadingDao
import com.shuremind.data.dao.ReminderRuleDao
import com.shuremind.data.dao.TagDao
import com.shuremind.data.dao.TaskDao
import com.shuremind.data.dao.TaskTagDao
import com.shuremind.data.repo.DeliveryWatermarkRepository
import com.shuremind.data.repo.MeterSeedRepository
import com.shuremind.data.repo.ScheduleChangeNotifier
import com.shuremind.data.repo.SettingsRepository
import com.shuremind.data.repo.TransactionRunner
import com.shuremind.data.repo.WeeklyReviewSeedRepository
import com.shuremind.engine.TaskType
import java.time.Duration
import java.time.LocalTime

/**
 * D-31: replace-all import. UI/ViewModels talk only to this repo, never the DAOs directly
 * (M2/M3 repo pattern). Fakeable for tests via [TransactionRunner] + DAO fakes (no real Room needed).
 */
interface ImportRepository {
    /**
     * Wipes every Room table and restores [file] verbatim in one transaction, then restores the
     * D-30 settings subset, sets the delivery watermark to [now] (prevents an overdue-summary flood
     * from imported history), restores the D-27/D-28 seed markers, and triggers a recompute+rearm.
     * ui_language is intentionally never applied (D-31/D-13). No partial state if the transaction fails.
     */
    suspend fun import(file: ExportFile, now: Long = System.currentTimeMillis())
}

internal class RoomImportRepository(
    private val transactionRunner: TransactionRunner,
    private val taskDao: TaskDao,
    private val reminderRuleDao: ReminderRuleDao,
    private val tagDao: TagDao,
    private val taskTagDao: TaskTagDao,
    private val completionLogDao: CompletionLogDao,
    private val meterReadingDao: MeterReadingDao,
    private val settingsRepository: SettingsRepository,
    private val deliveryWatermarkRepository: DeliveryWatermarkRepository,
    private val meterSeedRepository: MeterSeedRepository,
    private val weeklyReviewSeedRepository: WeeklyReviewSeedRepository,
    private val scheduleChangeNotifier: ScheduleChangeNotifier = ScheduleChangeNotifier.NONE
) : ImportRepository {

    override suspend fun import(file: ExportFile, now: Long) {
        transactionRunner.runInTransaction {
            // Children before parents: completion_log/task_tags have no ON DELETE CASCADE guarantee
            // we want to rely on here, so wipe explicitly in dependency order either way.
            completionLogDao.deleteAll()
            taskTagDao.deleteAll()
            reminderRuleDao.deleteAll()
            meterReadingDao.deleteAll()
            taskDao.deleteAll()
            tagDao.deleteAll()

            taskDao.insertAll(file.tasks.map { it.toEntity() })
            tagDao.insertAll(file.tags.map { it.toEntity() })
            taskTagDao.insertAll(file.taskTags.map { it.toEntity() })
            reminderRuleDao.insertAll(file.reminderRules.map { it.toEntity() })
            completionLogDao.insertAll(file.completions.map { it.toEntity() })
            meterReadingDao.insertAll(file.meterReadings.map { it.toEntity() })
        }

        restoreSettings(file.settings)
        deliveryWatermarkRepository.advanceTo(now)
        file.settings.weeklyReviewTaskId?.let { weeklyReviewSeedRepository.markSeeded(it) }
        file.settings.seededMeterNames.forEach { meterSeedRepository.markSeeded(it) }

        scheduleChangeNotifier.onScheduleChanged()
    }

    private suspend fun restoreSettings(dto: ExportSettingsDto) {
        settingsRepository.setQuietHours(LocalTime.parse(dto.quietHoursStart), LocalTime.parse(dto.quietHoursEnd))
        settingsRepository.setDefaultAllDayTime(LocalTime.parse(dto.defaultAllDayTime))
        settingsRepository.setCurrency(dto.currency)
        dto.defaultReminderOffsets.forEach { (typeName, offsets) ->
            runCatching { TaskType.valueOf(typeName) }.getOrNull()?.let { type ->
                settingsRepository.setDefaultReminderOffsets(type, offsets)
            }
        }
        settingsRepository.setSnoozePresets(dto.snoozePresetsMinutes.map { Duration.ofMinutes(it) })
        settingsRepository.setDefaultSnoozeDuration(Duration.ofMinutes(dto.defaultSnoozeDurationMinutes))
    }
}
