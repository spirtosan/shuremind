package com.shuremind.di

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.shuremind.data.AppDatabase
import com.shuremind.data.backup.BackupRepository
import com.shuremind.data.backup.ImportRepository
import com.shuremind.data.backup.RoomBackupRepository
import com.shuremind.data.backup.RoomImportRepository
import com.shuremind.data.repo.AutoBackupScheduling
import com.shuremind.data.repo.AutoBackupSchedulingNotifier
import com.shuremind.data.repo.BackupFileWriter
import com.shuremind.data.repo.BackupSettingsRepository
import com.shuremind.data.repo.CompletionRepository
import com.shuremind.data.repo.DataStoreBackupSettingsRepository
import com.shuremind.data.repo.DataStoreDeliveryWatermarkRepository
import com.shuremind.data.repo.DataStoreMeterSeedRepository
import com.shuremind.data.repo.DataStoreSettingsRepository
import com.shuremind.data.repo.DataStoreWeeklyReviewSeedRepository
import com.shuremind.data.repo.DeliveryWatermarkRepository
import com.shuremind.data.repo.LanguageRepository
import com.shuremind.data.repo.MeterRepository
import com.shuremind.data.repo.MeterSeedRepository
import com.shuremind.data.repo.ReminderRuleRepository
import com.shuremind.data.repo.RoomCompletionRepository
import com.shuremind.data.repo.RoomReminderRuleRepository
import com.shuremind.data.repo.RoomTagRepository
import com.shuremind.data.repo.RoomTaskRepository
import com.shuremind.data.repo.RoomTransactionRunner
import com.shuremind.data.repo.RoomWindowConversionRepository
import com.shuremind.data.repo.ScheduleChangeNotifier
import com.shuremind.data.repo.SettingsRepository
import com.shuremind.data.repo.TagRepository
import com.shuremind.data.repo.TaskRepository
import com.shuremind.data.repo.WeeklyReviewSeedRepository
import com.shuremind.data.repo.WindowConversionRepository
import com.shuremind.system.AlarmArmer
import com.shuremind.system.AlarmScheduler
import com.shuremind.system.BackupManager
import com.shuremind.system.BackupWorker
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
    val tagRepository: TagRepository = RoomTagRepository(database.tagDao(), database.taskTagDao(), RoomTransactionRunner(database))
    val reminderRuleRepository: ReminderRuleRepository = RoomReminderRuleRepository(database.reminderRuleDao())
    val windowConversionRepository: WindowConversionRepository = RoomWindowConversionRepository(
        database, database.taskDao(), database.reminderRuleDao(), scheduleChangeNotifier
    )
    val settingsRepository: SettingsRepository =
        DataStoreSettingsRepository(context.applicationContext.settingsDataStore, scheduleChangeNotifier)
    val languageRepository: LanguageRepository = LanguageRepository()
    val deliveryWatermarkRepository: DeliveryWatermarkRepository =
        DataStoreDeliveryWatermarkRepository(context.applicationContext.settingsDataStore)
    val meterSeedRepository: MeterSeedRepository = DataStoreMeterSeedRepository(context.applicationContext.settingsDataStore)
    val weeklyReviewSeedRepository: WeeklyReviewSeedRepository =
        DataStoreWeeklyReviewSeedRepository(context.applicationContext.settingsDataStore)

    // M5 (D-32): a settings write toggles the daily BackupWorker via this hook, mirroring scheduleChangeNotifier above.
    val backupSettingsRepository: BackupSettingsRepository = DataStoreBackupSettingsRepository(
        context.applicationContext.settingsDataStore,
        notifier = AutoBackupSchedulingNotifier { enabled, folderUri ->
            if (AutoBackupScheduling.shouldBeEnqueued(enabled, folderUri)) {
                BackupWorker.enqueue(context.applicationContext)
            } else {
                BackupWorker.cancel(context.applicationContext)
            }
        }
    )
    val backupFileWriter: BackupFileWriter = BackupManager(context.applicationContext)
    val backupRepository: BackupRepository = RoomBackupRepository(
        taskDao = database.taskDao(),
        reminderRuleDao = database.reminderRuleDao(),
        tagDao = database.tagDao(),
        taskTagDao = database.taskTagDao(),
        completionLogDao = database.completionLogDao(),
        meterReadingDao = database.meterReadingDao(),
        settingsRepository = settingsRepository,
        languageRepository = languageRepository,
        meterSeedRepository = meterSeedRepository,
        weeklyReviewSeedRepository = weeklyReviewSeedRepository
    )
    val importRepository: ImportRepository = RoomImportRepository(
        transactionRunner = RoomTransactionRunner(database),
        taskDao = database.taskDao(),
        reminderRuleDao = database.reminderRuleDao(),
        tagDao = database.tagDao(),
        taskTagDao = database.taskTagDao(),
        completionLogDao = database.completionLogDao(),
        meterReadingDao = database.meterReadingDao(),
        settingsRepository = settingsRepository,
        deliveryWatermarkRepository = deliveryWatermarkRepository,
        meterSeedRepository = meterSeedRepository,
        weeklyReviewSeedRepository = weeklyReviewSeedRepository,
        scheduleChangeNotifier = scheduleChangeNotifier
    )

    private val alarmArmer: AlarmArmer = AlarmScheduler(context.applicationContext)
    val notificationCenter: NotificationCenter =
        NotificationCenter(context.applicationContext, weeklyReviewSeedRepository = weeklyReviewSeedRepository)

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
