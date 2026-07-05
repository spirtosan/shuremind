package com.shuremind.di

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.shuremind.data.AppDatabase
import com.shuremind.data.repo.CompletionRepository
import com.shuremind.data.repo.DataStoreSettingsRepository
import com.shuremind.data.repo.LanguageRepository
import com.shuremind.data.repo.MeterRepository
import com.shuremind.data.repo.ReminderRuleRepository
import com.shuremind.data.repo.RoomCompletionRepository
import com.shuremind.data.repo.RoomTagRepository
import com.shuremind.data.repo.RoomTaskRepository
import com.shuremind.data.repo.SettingsRepository
import com.shuremind.data.repo.TagRepository
import com.shuremind.data.repo.TaskRepository

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

/**
 * Manual DI (no Hilt, D-16): built once in [com.shuremind.ShuRemindApplication] and held for the
 * app's lifetime. ViewModels receive repositories only, never the database or DAOs directly —
 * see [ViewModelFactory].
 */
class AppContainer(context: Context) {

    private val database: AppDatabase = Room.databaseBuilder(
        context.applicationContext,
        AppDatabase::class.java,
        AppDatabase.DATABASE_NAME
    ).build()

    val taskRepository: TaskRepository = RoomTaskRepository(database.taskDao())
    val completionRepository: CompletionRepository =
        RoomCompletionRepository(database, database.completionLogDao(), database.taskDao())
    val meterRepository: MeterRepository = MeterRepository(database.meterReadingDao())
    val tagRepository: TagRepository = RoomTagRepository(database.tagDao(), database.taskTagDao())
    val reminderRuleRepository: ReminderRuleRepository = ReminderRuleRepository(database.reminderRuleDao())
    val settingsRepository: SettingsRepository = DataStoreSettingsRepository(context.applicationContext.settingsDataStore)
    val languageRepository: LanguageRepository = LanguageRepository()
}
