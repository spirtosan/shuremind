package com.shuremind.data.backup.dto

import com.shuremind.engine.RecurrenceAnchor
import com.shuremind.engine.RecurrenceFrequency
import com.shuremind.engine.TaskStatus
import com.shuremind.engine.TaskType
import com.shuremind.engine.CompletionAction
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Root of the versioned full-fidelity export/import JSON (DATA_MODEL.md "Export JSON", D-11/D-30/D-31).
 * Field names mirror DATA_MODEL.md's Room columns 1:1 so the file is self-describing; pure Kotlin,
 * no Room/Android imports — decoupled from the live entity schema on purpose (sync foundation).
 */
@Serializable
data class ExportFile(
    @SerialName("schema_version") val schemaVersion: Int,
    @SerialName("app_version") val appVersion: String,
    @SerialName("exported_at") val exportedAt: Long,
    val tasks: List<TaskDto>,
    @SerialName("reminder_rules") val reminderRules: List<ReminderRuleDto>,
    val tags: List<TagDto>,
    @SerialName("task_tags") val taskTags: List<TaskTagDto>,
    val completions: List<CompletionLogDto>,
    @SerialName("meter_readings") val meterReadings: List<MeterReadingDto>,
    val settings: ExportSettingsDto
)

@Serializable
data class TaskDto(
    val id: String,
    val title: String,
    val notes: String?,
    val type: TaskType,
    val status: TaskStatus,
    val impact: Int,
    val urgency: Int,
    @SerialName("estimated_cost") val estimatedCost: Double?,
    @SerialName("due_local_date") val dueLocalDate: String?,
    @SerialName("due_local_time") val dueLocalTime: String?,
    @SerialName("not_before") val notBefore: String?,
    @SerialName("rec_freq") val recFreq: RecurrenceFrequency?,
    @SerialName("rec_interval") val recInterval: Int,
    @SerialName("rec_anchor") val recAnchor: RecurrenceAnchor?,
    @SerialName("rec_days_of_week") val recDaysOfWeek: String?,
    @SerialName("rec_day_of_month") val recDayOfMonth: Int?,
    @SerialName("rec_times_of_day") val recTimesOfDay: String?,
    @SerialName("rec_end_date") val recEndDate: String?,
    @SerialName("nag_interval_hours") val nagIntervalHours: Double?,
    @SerialName("stock_qty") val stockQty: Double?,
    @SerialName("dose_per_intake") val dosePerIntake: Double?,
    @SerialName("restock_lead_days") val restockLeadDays: Int?,
    @SerialName("stock_recorded_at") val stockRecordedAt: String?,
    @SerialName("meter_name") val meterName: String?,
    @SerialName("meter_interval") val meterInterval: Double?,
    @SerialName("last_done_meter") val lastDoneMeter: Double?,
    @SerialName("window_hint") val windowHint: String?,
    /** D-42: added after schema_version 1 shipped; defaults to false so pre-M7 backup files (missing this key) import cleanly. */
    @SerialName("alarm_mode") val alarmMode: Boolean = false,
    @SerialName("snoozed_until") val snoozedUntil: Long?,
    @SerialName("next_fire_at") val nextFireAt: Long?,
    @SerialName("created_at") val createdAt: Long,
    @SerialName("updated_at") val updatedAt: Long,
    @SerialName("deleted_at") val deletedAt: Long?,
    val dirty: Int
)

@Serializable
data class ReminderRuleDto(
    val id: String,
    @SerialName("task_id") val taskId: String,
    @SerialName("offset_iso") val offsetIso: String
)

@Serializable
data class TagDto(
    val id: String,
    val name: String,
    val color: String?
)

@Serializable
data class TaskTagDto(
    @SerialName("task_id") val taskId: String,
    @SerialName("tag_id") val tagId: String
)

@Serializable
data class CompletionLogDto(
    val id: String,
    @SerialName("task_id") val taskId: String,
    @SerialName("occurrence_local") val occurrenceLocal: String,
    val action: CompletionAction,
    @SerialName("completed_at") val completedAt: Long,
    @SerialName("meter_value") val meterValue: Double?,
    val note: String?
)

@Serializable
data class MeterReadingDto(
    val id: String,
    @SerialName("meter_name") val meterName: String,
    val value: Double,
    @SerialName("recorded_at") val recordedAt: Long
)

/**
 * D-30: the DataStore settings subset that travels with an export. Device-specific settings
 * (backup folder URI, auto-backup toggle, exact-alarms opt-in, delivery watermark) are excluded on
 * purpose — they describe *this device*, not the data. [uiLanguage] is exported for reference but
 * D-31/D-13 mandate it is never applied back on import (a phone keeps its own language).
 */
@Serializable
data class ExportSettingsDto(
    @SerialName("quiet_hours_start") val quietHoursStart: String,
    @SerialName("quiet_hours_end") val quietHoursEnd: String,
    @SerialName("default_all_day_time") val defaultAllDayTime: String,
    val currency: String,
    /** Keyed by [TaskType.name]; unrecognized keys are ignored on import (forward tolerance). */
    @SerialName("default_reminder_offsets") val defaultReminderOffsets: Map<String, List<String>>,
    @SerialName("snooze_presets_minutes") val snoozePresetsMinutes: List<Long>,
    @SerialName("default_snooze_duration_minutes") val defaultSnoozeDurationMinutes: Long,
    @SerialName("weekly_review_task_id") val weeklyReviewTaskId: String?,
    @SerialName("seeded_meter_names") val seededMeterNames: Set<String>,
    /** D-21 UI language at export time; read-only round trip, D-31: never applied by import. */
    @SerialName("ui_language") val uiLanguage: String?
)
