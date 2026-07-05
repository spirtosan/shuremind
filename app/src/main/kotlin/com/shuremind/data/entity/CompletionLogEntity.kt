package com.shuremind.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.shuremind.engine.CompletionAction

@Entity(
    tableName = "completion_log",
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["task_id"]
        )
    ],
    indices = [Index(value = ["task_id"])]
)
data class CompletionLogEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "task_id") val taskId: String,
    @ColumnInfo(name = "occurrence_local") val occurrenceLocal: String,
    val action: CompletionAction,
    @ColumnInfo(name = "completed_at") val completedAt: Long,
    @ColumnInfo(name = "meter_value") val meterValue: Double?,
    val note: String?
)
