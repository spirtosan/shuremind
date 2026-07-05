package com.shuremind.di

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.shuremind.data.AppDatabase
import com.shuremind.data.repo.CompletionRepository
import com.shuremind.data.repo.DataStoreDeliveryWatermarkRepository
import com.shuremind.data.repo.DataStoreSettingsRepository
import com.shuremind.data.repo.DeliveryWatermarkRepository
import com.shuremind.data.repo.LanguageRepository
import com.shuremind.data.repo.MeterRepository
import com.shuremind.data.repo.ReminderRuleRepository
import com.shuremind.data.repo.RoomCompletionRepository
import com.shuremind.data.repo.RoomReminderRuleRepository
import com.shuremind.data.repo.RoomTagRepository
import com.shuremind.data.repo.RoomTaskRepository
import com.shuremind.data.repo.ScheduleChangeNotifier
import com.shuremind.data.repo.SettingsRepository
import com.shuremind.data.repo.TagRepository
import com.shuremind.data.repo.TaskRepository
import com.shuremind.system.AlarmArmer
import com.shuremind.system.AlarmScheduler
import com.shuremind.system.NotificationCenter
import com.shuremind.system.RecomputeAndRearm

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

    // Repository writes that can change scheduling call back into RecomputeAndRearm via this hook
    // (CLAUDE.md STEP 3: "wire via repository-level hooks, not scattered UI calls"). RecomputeAndRearm
    // itself needs these same repositories, so the hook is a forwarding indirection set up *after*
    // RecomputeAndRearm exists below, breaking the circular construction order.
    private var scheduleChangeHook: ScheduleChangeNotifier = ScheduleChangeNotifier.NONE
    private val scheduleChangeNotifier = ScheduleChangeNotifier { scheduleChangeHook.onScheduleChanged() }

    val taskRepository: TaskRepository = RoomTaskRepository(database.taskDao(), scheduleChangeNotifier)
    val completionRepository: CompletionRepository =
        RoomCompletionRepository(database, database.completionLogDao(), database.taskDao(), scheduleChangeNotifier)
    val meterRepository: MeterRepository = MeterRepository(database.meterReadingDao())
    val tagRepository: TagRepository = RoomTagRepository(database.tagDao(), database.taskTagDao())
    val reminderRuleRepository: ReminderRuleRepository = RoomReminderRuleRepository(database.reminderRuleDao())
    val settingsRepository: SettingsRepository =
        DataStoreSettingsRepository(context.applicationContext.settingsDataStore, scheduleChangeNotifier)
    val languageRepository: LanguageRepository = LanguageRepository()
    val deliveryWatermarkRepository: DeliveryWatermarkRepository =
        DataStoreDeliveryWatermarkRepository(context.applicationContext.settingsDataStore)

    private val alarmArmer: AlarmArmer = AlarmScheduler(context.applicationContext)
    val notificationCenter: NotificationCenter = NotificationCenter(context.applicationContext)

    /** M3 (D-07/D-10/D-23) single entry point — see [RecomputeAndRearm]'s own kdoc for call sites. */
    val recomputeAndRearm: RecomputeAndRearm = RecomputeAndRearm(
        taskRepository = taskRepository,
        completionRepository = completionRepository,
        reminderRuleRepository = reminderRuleRepository,
        settingsRepository = settingsRepository,
        deliveryWatermarkRepository = deliveryWatermarkRepository,
        alarmArmer = alarmArmer,
        overdueSummaryNotifier = notificationCenter
    )

    init {
        scheduleChangeHook = ScheduleChangeNotifier { recomputeAndRearm.run() }
    }
}
