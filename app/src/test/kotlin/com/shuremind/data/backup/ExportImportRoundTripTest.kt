package com.shuremind.data.backup

import com.shuremind.data.backup.dto.ExportSettingsDto
import com.shuremind.data.entity.CompletionLogEntity
import com.shuremind.data.entity.MeterReadingEntity
import com.shuremind.data.entity.ReminderRuleEntity
import com.shuremind.data.entity.TagEntity
import com.shuremind.data.entity.TaskEntity
import com.shuremind.data.entity.TaskTagEntity
import com.shuremind.engine.CompletionAction
import com.shuremind.engine.RecurrenceAnchor
import com.shuremind.engine.RecurrenceFrequency
import com.shuremind.engine.TaskStatus
import com.shuremind.engine.TaskType
import com.shuremind.testutil.FakeCompletionLogDao
import com.shuremind.testutil.FakeDeliveryWatermarkRepository
import com.shuremind.testutil.FakeMeterSeedRepository
import com.shuremind.testutil.FakeReminderRuleDao
import com.shuremind.testutil.FakeSettingsRepository
import com.shuremind.testutil.FakeTagDao
import com.shuremind.testutil.FakeTaskDao
import com.shuremind.testutil.FakeTaskTagDao
import com.shuremind.testutil.FakeMeterReadingDao
import com.shuremind.testutil.FakeWeeklyReviewSeedRepository
import com.shuremind.testutil.NoOpTransactionRunner
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.time.Duration
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportImportRoundTripTest {

    // --- Fixture data covering all 8 task types + soft-deleted + tags + completions + meter readings ---

    private fun baseTask(
        id: String,
        type: TaskType,
        status: TaskStatus = TaskStatus.ACTIVE,
        deletedAt: Long? = null
    ) = TaskEntity(
        id = id,
        title = "Task $id",
        notes = "notes for $id",
        type = type,
        status = status,
        impact = 2,
        urgency = 1,
        estimatedCost = 12.5,
        dueLocalDate = "2026-08-01",
        dueLocalTime = "09:30",
        notBefore = "2026-07-10",
        recFreq = RecurrenceFrequency.WEEKLY,
        recInterval = 2,
        recAnchor = RecurrenceAnchor.CALENDAR,
        recDaysOfWeek = "MO,WE,FR",
        recDayOfMonth = 15,
        recTimesOfDay = "08:00,20:00",
        recEndDate = "2027-01-01",
        nagIntervalHours = 24.0,
        stockQty = 30.0,
        dosePerIntake = 2.0,
        restockLeadDays = 5,
        stockRecordedAt = "2026-07-01",
        meterName = "car",
        meterInterval = 10000.0,
        lastDoneMeter = 45000.0,
        windowHint = "usually Sep-Nov",
        snoozedUntil = 1_700_000_500_000L,
        nextFireAt = 1_700_000_000_000L,
        createdAt = 1_699_000_000_000L,
        updatedAt = 1_699_500_000_000L,
        deletedAt = deletedAt,
        dirty = 1
    )

    private val tasks = TaskType.entries.mapIndexed { index, type ->
        // D-42: EVENT (index 0) also covers the alarmMode=true round trip; every other type stays
        // at the field default (false), so the full false/true range is exercised across the set.
        baseTask(id = "task-$index-${type.name}", type = type).let { if (type == TaskType.EVENT) it.copy(alarmMode = true) else it }
    } + baseTask(id = "task-deleted", type = TaskType.EVENT, status = TaskStatus.DONE, deletedAt = 1_699_600_000_000L)

    private val reminderRules = listOf(
        ReminderRuleEntity(id = "rule-1", taskId = tasks[0].id, offsetIso = "P14D"),
        ReminderRuleEntity(id = "rule-2", taskId = tasks[0].id, offsetIso = "P1D")
    )

    private val tags = listOf(
        TagEntity(id = "tag-home", name = "home", color = null),
        TagEntity(id = "tag-shop", name = "shop", color = "#FF0000")
    )

    private val taskTags = listOf(
        TaskTagEntity(taskId = tasks[0].id, tagId = tags[0].id),
        TaskTagEntity(taskId = tasks[0].id, tagId = tags[1].id)
    )

    private val completions = listOf(
        CompletionLogEntity(
            id = "completion-1",
            taskId = tasks[0].id,
            occurrenceLocal = "2026-07-01 09:30",
            action = CompletionAction.DONE,
            completedAt = 1_699_100_000_000L,
            meterValue = 45500.0,
            note = "done early"
        ),
        CompletionLogEntity(
            id = "completion-2",
            taskId = tasks[1].id,
            occurrenceLocal = "2026-07-02",
            action = CompletionAction.SKIPPED,
            completedAt = 1_699_200_000_000L,
            meterValue = null,
            note = null
        )
    )

    private val meterReadings = listOf(
        MeterReadingEntity(id = "meter-1", meterName = "car", value = 45500.0, recordedAt = 1_699_150_000_000L)
    )

    private val settingsDto = ExportSettingsDto(
        quietHoursStart = "22:00",
        quietHoursEnd = "08:00",
        defaultAllDayTime = "09:00",
        currency = "EUR",
        defaultReminderOffsets = mapOf(
            TaskType.EVENT.name to listOf("PT2H"),
            TaskType.ANNIVERSARY.name to listOf("P14D", "P1D"),
            TaskType.DEADLINE.name to listOf("P14D", "P7D", "P1D")
        ),
        snoozePresetsMinutes = listOf(60L, 240L, 1440L),
        defaultSnoozeDurationMinutes = 60L,
        weeklyReviewTaskId = tasks[0].id,
        seededMeterNames = setOf("car"),
        uiLanguage = "ru"
    )

    private val snapshot = BackupSnapshot(
        tasks = tasks,
        reminderRules = reminderRules,
        tags = tags,
        taskTags = taskTags,
        completions = completions,
        meterReadings = meterReadings,
        settings = settingsDto
    )

    @Test
    fun `export then import round trips every entity list exactly`() {
        val file = ExportEngine.buildExportFile(snapshot, appVersion = "0.1", exportedAt = 1_699_999_000_000L)
        val json = ExportEngine.toJson(file)

        val parsed = ImportEngine.parse(json)
        check(parsed is ImportEngine.ParseResult.Success)

        assertEquals(tasks, parsed.file.tasks.map { it.toEntity() })
        assertEquals(reminderRules, parsed.file.reminderRules.map { it.toEntity() })
        assertEquals(tags, parsed.file.tags.map { it.toEntity() })
        assertEquals(taskTags, parsed.file.taskTags.map { it.toEntity() })
        assertEquals(completions, parsed.file.completions.map { it.toEntity() })
        assertEquals(meterReadings, parsed.file.meterReadings.map { it.toEntity() })
        assertEquals(settingsDto, parsed.file.settings)
        assertEquals(1, parsed.file.schemaVersion)
    }

    @Test
    fun `import into fakes restores every table verbatim, sets watermark to import time, never applies ui_language`() = runTest {
        val file = ExportEngine.buildExportFile(snapshot, appVersion = "0.1", exportedAt = 1_699_999_000_000L)

        val taskDao = FakeTaskDao()
        val reminderRuleDao = FakeReminderRuleDao()
        val tagDao = FakeTagDao()
        val taskTagDao = FakeTaskTagDao()
        val completionLogDao = FakeCompletionLogDao()
        val meterReadingDao = FakeMeterReadingDao()
        val settingsRepository = FakeSettingsRepository()
        val watermarkRepository = FakeDeliveryWatermarkRepository(initial = 0L)
        val meterSeedRepository = FakeMeterSeedRepository()
        val weeklyReviewSeedRepository = FakeWeeklyReviewSeedRepository()

        val importRepository = RoomImportRepository(
            transactionRunner = NoOpTransactionRunner,
            taskDao = taskDao,
            reminderRuleDao = reminderRuleDao,
            tagDao = tagDao,
            taskTagDao = taskTagDao,
            completionLogDao = completionLogDao,
            meterReadingDao = meterReadingDao,
            settingsRepository = settingsRepository,
            deliveryWatermarkRepository = watermarkRepository,
            meterSeedRepository = meterSeedRepository,
            weeklyReviewSeedRepository = weeklyReviewSeedRepository
        )

        val importTime = 1_800_000_000_000L
        importRepository.import(file, now = importTime)

        assertEquals(tasks.toSet(), taskDao.getAllForExport().toSet())
        assertEquals(reminderRules.toSet(), reminderRuleDao.getAllForExport().toSet())
        assertEquals(tags.toSet(), tagDao.observeAll().first().toSet())
        assertEquals(taskTags.toSet(), taskTagDao.observeAll().first().toSet())
        assertEquals(completions.toSet(), completionLogDao.getAllForExport().toSet())
        assertEquals(meterReadings.toSet(), meterReadingDao.observeAll().first().toSet())

        assertEquals(importTime, watermarkRepository.get())
        assertEquals(setOf("car"), meterSeedRepository.getAllSeeded())
        assertEquals(tasks[0].id, weeklyReviewSeedRepository.seededTaskId())

        val restoredSettings = settingsRepository.settings.first()
        assertEquals(LocalTime.parse("22:00"), restoredSettings.quietHoursStart)
        assertEquals(LocalTime.parse("08:00"), restoredSettings.quietHoursEnd)
        assertEquals("EUR", restoredSettings.currency)
        assertEquals(listOf(Duration.ofMinutes(60), Duration.ofMinutes(240), Duration.ofMinutes(1440)), restoredSettings.snoozePresets)

        // D-31/D-13: ui_language is exported for reference but RoomImportRepository has no
        // LanguageRepository dependency at all — there is no code path that could apply it.
    }

    @Test
    fun `schema version other than 1 is rejected`() {
        val file = ExportEngine.buildExportFile(snapshot, appVersion = "0.1", exportedAt = 0L)
        val json = ExportEngine.toJson(file).replace("\"schema_version\":1", "\"schema_version\":2")

        val result = ImportEngine.parse(json)

        assertTrue(result is ImportEngine.ParseResult.UnsupportedSchemaVersion)
        assertEquals(2, (result as ImportEngine.ParseResult.UnsupportedSchemaVersion).found)
    }

    @Test
    fun `garbage input is rejected as malformed`() {
        val result = ImportEngine.parse("{ this is not valid json ")

        assertTrue(result is ImportEngine.ParseResult.Malformed)
    }

    @Test
    fun `unknown top-level keys are tolerated`() {
        val file = ExportEngine.buildExportFile(snapshot, appVersion = "0.1", exportedAt = 0L)
        val json = ExportEngine.toJson(file)
        val withUnknownKey = json.dropLast(1) + ",\"future_field_from_a_newer_version\":123}"

        val result = ImportEngine.parse(withUnknownKey)

        assertTrue(result is ImportEngine.ParseResult.Success)
    }

    // --- D-42: alarm_mode was added after schema_version 1 shipped; old backup files never had this key ---

    @Test
    fun `a pre-M7 backup file without alarm_mode imports with every task defaulting to false`() {
        val file = ExportEngine.buildExportFile(snapshot, appVersion = "0.1", exportedAt = 0L)
        val json = ExportEngine.toJson(file)
        check(json.contains("\"alarm_mode\":true")) { "fixture must contain at least one alarmMode=true task to make this test meaningful" }
        val oldFormatJson = json.replace(Regex(",\"alarm_mode\":(true|false)"), "")

        val result = ImportEngine.parse(oldFormatJson)

        check(result is ImportEngine.ParseResult.Success)
        assertTrue(result.file.tasks.isNotEmpty())
        assertTrue(result.file.tasks.all { !it.alarmMode })
    }
}
