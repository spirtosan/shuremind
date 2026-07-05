package com.shuremind.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "reminder_rules",
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["task_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["task_id"])]
)
data class ReminderRuleEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "task_id") val taskId: String,
    @ColumnInfo(name = "offset_iso") val offsetIso: String
)
