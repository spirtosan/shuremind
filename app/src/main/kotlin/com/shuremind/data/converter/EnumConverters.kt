package com.shuremind.data.converter

import androidx.room.TypeConverter
import com.shuremind.engine.CompletionAction
import com.shuremind.engine.RecurrenceAnchor
import com.shuremind.engine.RecurrenceFrequency
import com.shuremind.engine.TaskStatus
import com.shuremind.engine.TaskType

class EnumConverters {

    @TypeConverter
    fun fromTaskType(value: TaskType): String = value.name

    @TypeConverter
    fun toTaskType(value: String): TaskType = TaskType.valueOf(value)

    @TypeConverter
    fun fromTaskStatus(value: TaskStatus): String = value.name

    @TypeConverter
    fun toTaskStatus(value: String): TaskStatus = TaskStatus.valueOf(value)

    @TypeConverter
    fun fromRecurrenceFrequency(value: RecurrenceFrequency?): String? = value?.name

    @TypeConverter
    fun toRecurrenceFrequency(value: String?): RecurrenceFrequency? = value?.let { RecurrenceFrequency.valueOf(it) }

    @TypeConverter
    fun fromRecurrenceAnchor(value: RecurrenceAnchor?): String? = value?.name

    @TypeConverter
    fun toRecurrenceAnchor(value: String?): RecurrenceAnchor? = value?.let { RecurrenceAnchor.valueOf(it) }

    @TypeConverter
    fun fromCompletionAction(value: CompletionAction): String = value.name

    @TypeConverter
    fun toCompletionAction(value: String): CompletionAction = CompletionAction.valueOf(value)
}
