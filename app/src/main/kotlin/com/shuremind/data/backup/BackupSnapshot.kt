package com.shuremind.data.backup

import com.shuremind.data.backup.dto.ExportSettingsDto
import com.shuremind.data.entity.CompletionLogEntity
import com.shuremind.data.entity.MeterReadingEntity
import com.shuremind.data.entity.ReminderRuleEntity
import com.shuremind.data.entity.TagEntity
import com.shuremind.data.entity.TaskEntity
import com.shuremind.data.entity.TaskTagEntity

/** Everything [ExportEngine] needs to build an [com.shuremind.data.backup.dto.ExportFile], already read from storage. */
data class BackupSnapshot(
    val tasks: List<TaskEntity>,
    val reminderRules: List<ReminderRuleEntity>,
    val tags: List<TagEntity>,
    val taskTags: List<TaskTagEntity>,
    val completions: List<CompletionLogEntity>,
    val meterReadings: List<MeterReadingEntity>,
    val settings: ExportSettingsDto
)
