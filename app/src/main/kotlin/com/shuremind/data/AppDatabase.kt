package com.shuremind.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.shuremind.data.converter.EnumConverters
import com.shuremind.data.dao.CompletionLogDao
import com.shuremind.data.dao.MeterReadingDao
import com.shuremind.data.dao.ReminderRuleDao
import com.shuremind.data.dao.TagDao
import com.shuremind.data.dao.TaskDao
import com.shuremind.data.dao.TaskTagDao
import com.shuremind.data.entity.CompletionLogEntity
import com.shuremind.data.entity.MeterReadingEntity
import com.shuremind.data.entity.ReminderRuleEntity
import com.shuremind.data.entity.TagEntity
import com.shuremind.data.entity.TaskEntity
import com.shuremind.data.entity.TaskTagEntity

@Database(
    entities = [
        TaskEntity::class,
        ReminderRuleEntity::class,
        TagEntity::class,
        TaskTagEntity::class,
        CompletionLogEntity::class,
        MeterReadingEntity::class
    ],
    version = 2,
    exportSchema = true
)
@TypeConverters(EnumConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun reminderRuleDao(): ReminderRuleDao
    abstract fun tagDao(): TagDao
    abstract fun taskTagDao(): TaskTagDao
    abstract fun completionLogDao(): CompletionLogDao
    abstract fun meterReadingDao(): MeterReadingDao

    companion object {
        const val DATABASE_NAME = "shuremind.db"
    }
}
