package com.shuremind.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.shuremind.engine.RecurrenceAnchor
import com.shuremind.engine.RecurrenceFrequency
import com.shuremind.engine.TaskStatus
import com.shuremind.engine.TaskType

@Entity(
    tableName = "tasks",
    indices = [
        Index(value = ["next_fire_at"]),
        Index(value = ["status", "deleted_at"]),
        Index(value = ["type"])
    ]
)
data class TaskEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val notes: String?,
    val type: TaskType,
    val status: TaskStatus,
    val impact: Int = 1,
    val urgency: Int = 1,
    @ColumnInfo(name = "estimated_cost") val estimatedCost: Double?,
    @ColumnInfo(name = "due_local_date") val dueLocalDate: String?,
    @ColumnInfo(name = "due_local_time") val dueLocalTime: String?,
    @ColumnInfo(name = "not_before") val notBefore: String?,
    @ColumnInfo(name = "rec_freq") val recFreq: RecurrenceFrequency?,
    @ColumnInfo(name = "rec_interval") val recInterval: Int = 1,
    @ColumnInfo(name = "rec_anchor") val recAnchor: RecurrenceAnchor?,
    @ColumnInfo(name = "rec_days_of_week") val recDaysOfWeek: String?,
    @ColumnInfo(name = "rec_day_of_month") val recDayOfMonth: Int?,
    @ColumnInfo(name = "rec_times_of_day") val recTimesOfDay: String?,
    @ColumnInfo(name = "rec_end_date") val recEndDate: String?,
    @ColumnInfo(name = "nag_interval_hours") val nagIntervalHours: Double?,
    @ColumnInfo(name = "stock_qty") val stockQty: Double?,
    @ColumnInfo(name = "dose_per_intake") val dosePerIntake: Double?,
    @ColumnInfo(name = "restock_lead_days") val restockLeadDays: Int?,
    @ColumnInfo(name = "stock_recorded_at") val stockRecordedAt: String?,
    @ColumnInfo(name = "meter_name") val meterName: String?,
    @ColumnInfo(name = "meter_interval") val meterInterval: Double?,
    @ColumnInfo(name = "last_done_meter") val lastDoneMeter: Double?,
    @ColumnInfo(name = "window_hint") val windowHint: String?,
    /** D-42: opt-in per-task alarm mode (Room v3, additive). Available on all types except SOMEDAY. */
    @ColumnInfo(name = "alarm_mode", defaultValue = "0") val alarmMode: Boolean = false,
    @ColumnInfo(name = "snoozed_until") val snoozedUntil: Long?,
    @ColumnInfo(name = "next_fire_at") val nextFireAt: Long?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long?,
    val dirty: Int = 1
)
