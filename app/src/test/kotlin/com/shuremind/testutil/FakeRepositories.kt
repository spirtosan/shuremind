package com.shuremind.testutil

import com.shuremind.data.dao.MeterReadingDao
import com.shuremind.data.entity.CompletionLogEntity
import com.shuremind.data.entity.MeterReadingEntity
import com.shuremind.data.entity.ReminderRuleEntity
import com.shuremind.data.entity.TagEntity
import com.shuremind.data.entity.TaskEntity
import com.shuremind.data.repo.AppSettings
import com.shuremind.data.repo.CompletionRepository
import com.shuremind.data.repo.DeliveryWatermarkRepository
import com.shuremind.data.repo.MeterRepository
import com.shuremind.data.repo.MeterSeedRepository
import com.shuremind.data.repo.NextFireAtCalculator
import com.shuremind.data.repo.ReminderRuleRepository
import com.shuremind.data.repo.SettingsRepository
import com.shuremind.data.repo.TagRepository
import com.shuremind.data.repo.TaskRepository
import com.shuremind.data.repo.WeeklyReviewSeedRepository
import com.shuremind.data.repo.WindowConversionRepository
import com.shuremind.engine.CompletionAction
import com.shuremind.engine.TaskStatus
import com.shuremind.engine.TaskType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Builds a minimal Task row for tests; only the fields a given test cares about need overriding. */
fun fixtureTask(
    id: String,
    title: String = id,
    type: TaskType = TaskType.EVENT,
    status: TaskStatus = TaskStatus.ACTIVE,
    impact: Int = 1,
    urgency: Int = 1,
    nextFireAt: Long? = null,
    snoozedUntil: Long? = null,
    createdAt: Long = 0L,
    deletedAt: Long? = null,
    meterName: String? = null,
    meterInterval: Double? = null,
    lastDoneMeter: Double? = null
): TaskEntity = TaskEntity(
    id = id,
    title = title,
    notes = null,
    type = type,
    status = status,
    impact = impact,
    urgency = urgency,
    estimatedCost = null,
    dueLocalDate = null,
    dueLocalTime = null,
    notBefore = null,
    recFreq = null,
    recInterval = 1,
    recAnchor = null,
    recDaysOfWeek = null,
    recDayOfMonth = null,
    recTimesOfDay = null,
    recEndDate = null,
    nagIntervalHours = null,
    stockQty = null,
    dosePerIntake = null,
    restockLeadDays = null,
    stockRecordedAt = null,
    meterName = meterName,
    meterInterval = meterInterval,
    lastDoneMeter = lastDoneMeter,
    windowHint = null,
    snoozedUntil = snoozedUntil,
    nextFireAt = nextFireAt,
    createdAt = createdAt,
    updatedAt = createdAt,
    deletedAt = deletedAt,
    dirty = 1
)

class FakeTaskRepository(initial: List<TaskEntity> = emptyList()) : TaskRepository {
    private val state = MutableStateFlow(initial)

    val tasks: List<TaskEntity> get() = state.value
    var lastSnoozedTaskId: String? = null
        private set
    var lastSnoozeUntil: Long? = null
        private set

    override suspend fun upsert(task: TaskEntity, now: Long) {
        state.update { list -> list.filterNot { it.id == task.id } + task }
    }

    override suspend fun update(task: TaskEntity, now: Long) {
        state.update { list -> list.map { if (it.id == task.id) task.copy(updatedAt = now, dirty = 1) else it } }
    }

    override suspend fun softDelete(id: String, now: Long) {
        state.update { list -> list.map { if (it.id == id) it.copy(deletedAt = now, updatedAt = now) else it } }
    }

    override suspend fun getById(id: String): TaskEntity? = state.value.find { it.id == id }

    override suspend fun snooze(task: TaskEntity, until: Long, now: Long) {
        lastSnoozedTaskId = task.id
        lastSnoozeUntil = until
        state.update { list -> list.map { if (it.id == task.id) it.copy(snoozedUntil = until, updatedAt = now) else it } }
    }

    override fun observeActive(excludedStatus: TaskStatus): Flow<List<TaskEntity>> =
        state.map { list -> list.filter { it.deletedAt == null && it.status != excludedStatus } }

    override fun observeByType(type: TaskType): Flow<List<TaskEntity>> =
        state.map { list -> list.filter { it.deletedAt == null && it.type == type } }

    override fun observeScheduled(): Flow<List<TaskEntity>> =
        state.map { list -> list.filter { it.deletedAt == null && it.nextFireAt != null }.sortedBy { it.nextFireAt } }
}

class FakeTagRepository : TagRepository {
    private val tags = MutableStateFlow<List<TagEntity>>(emptyList())
    private val taskTagIds = MutableStateFlow<Map<String, List<String>>>(emptyMap())

    override fun observeAll(): Flow<List<TagEntity>> = tags

    override fun observeTaskTagMap(): Flow<Map<String, List<TagEntity>>> =
        combine(tags, taskTagIds) { allTags, map ->
            val byId = allTags.associateBy { it.id }
            map.mapValues { (_, ids) -> ids.mapNotNull { byId[it] } }
        }

    override suspend fun getOrCreate(name: String): TagEntity {
        val normalized = name.trim().lowercase()
        tags.value.find { it.name == normalized }?.let { return it }
        val tag = TagEntity(id = "tag-$normalized", name = normalized, color = null)
        tags.update { it + tag }
        return tag
    }

    override suspend fun getTagIdsForTask(taskId: String): List<String> = taskTagIds.value[taskId].orEmpty()

    override suspend fun getTagsForTask(taskId: String): List<TagEntity> {
        val ids = taskTagIds.value[taskId].orEmpty().toSet()
        return tags.value.filter { it.id in ids }
    }

    override suspend fun setTagsForTask(taskId: String, tagIds: Set<String>) {
        taskTagIds.update { it + (taskId to tagIds.toList()) }
    }

    /** Test seam: pre-assign tags without going through getOrCreate. */
    fun seedTag(tag: TagEntity, taskIds: List<String> = emptyList()) {
        tags.update { it + tag }
        if (taskIds.isNotEmpty()) {
            taskTagIds.update { current ->
                current.toMutableMap().apply {
                    taskIds.forEach { taskId -> this[taskId] = (this[taskId].orEmpty() + tag.id) }
                }
            }
        }
    }
}

class FakeCompletionRepository : CompletionRepository {
    val recorded: MutableList<CompletionLogEntity> = mutableListOf()

    override suspend fun recordCompletion(entry: CompletionLogEntity, now: Long) {
        recorded += entry
    }

    override fun observeForTask(taskId: String): Flow<List<CompletionLogEntity>> = flowOf(emptyList())
    override suspend fun getLastByAction(taskId: String, action: CompletionAction): CompletionLogEntity? =
        recorded.filter { it.taskId == taskId && it.action == action }.maxByOrNull { it.completedAt }
    override suspend fun completeTask(task: TaskEntity, action: CompletionAction, now: Long, zone: ZoneId) = Unit
}

class FakeSettingsRepository(initial: AppSettings = AppSettings()) : SettingsRepository {
    private val state = MutableStateFlow(initial)

    override val settings: Flow<AppSettings> = state

    override suspend fun setQuietHours(start: LocalTime, end: LocalTime) {
        state.update { it.copy(quietHoursStart = start, quietHoursEnd = end) }
    }

    override suspend fun setDefaultAllDayTime(time: LocalTime) {
        state.update { it.copy(defaultAllDayTime = time) }
    }

    override suspend fun setCurrency(code: String) {
        state.update { it.copy(currency = code) }
    }

    override suspend fun setSnoozePresets(presets: List<Duration>) {
        state.update { it.copy(snoozePresets = presets) }
    }

    override suspend fun setDefaultReminderOffsets(type: TaskType, offsets: List<String>) {
        state.update { it.copy(defaultReminderOffsets = it.defaultReminderOffsets + (type to offsets)) }
    }

    override suspend fun setDefaultSnoozeDuration(duration: Duration) {
        state.update { it.copy(defaultSnoozeDuration = duration) }
    }

    override suspend fun setExactAlarmsOptIn(optIn: Boolean) {
        state.update { it.copy(exactAlarmsOptIn = optIn) }
    }
}

/** D-25: mirrors RoomWindowConversionRepository's field-level behavior over the in-memory fakes. */
class FakeWindowConversionRepository(
    private val taskRepository: FakeTaskRepository,
    private val reminderRuleRepository: FakeReminderRuleRepository,
    private val zone: ZoneId = ZoneId.systemDefault()
) : WindowConversionRepository {

    override suspend fun convertWindowToDeadline(
        taskId: String,
        dueLocalDate: LocalDate,
        dueLocalTime: LocalTime?,
        defaultReminderOffsets: List<String>,
        now: Long
    ): TaskEntity? {
        val existing = taskRepository.getById(taskId) ?: return null
        val converted = existing.copy(
            type = TaskType.DEADLINE,
            dueLocalDate = dueLocalDate.toString(),
            dueLocalTime = dueLocalTime?.toString(),
            recFreq = null,
            recInterval = 1,
            recAnchor = null,
            recDaysOfWeek = null,
            recDayOfMonth = null,
            recTimesOfDay = null,
            recEndDate = null,
            windowHint = null,
            updatedAt = now,
            dirty = 1
        )
        val nowZdt = Instant.ofEpochMilli(now).atZone(zone)
        val withNextFire = converted.copy(nextFireAt = NextFireAtCalculator.compute(converted, zone, nowZdt, lastDoneAt = null))
        taskRepository.update(withNextFire, now)
        reminderRuleRepository.setForTask(taskId, defaultReminderOffsets)
        return withNextFire
    }
}

class FakeDeliveryWatermarkRepository(initial: Long = 0L) : DeliveryWatermarkRepository {
    private var value: Long = initial

    override suspend fun get(): Long = value

    override suspend fun advanceTo(instant: Long) {
        value = instant
    }
}

class FakeMeterReadingDao(initial: List<MeterReadingEntity> = emptyList()) : MeterReadingDao {
    private val state = MutableStateFlow(initial)

    override suspend fun insert(reading: MeterReadingEntity) {
        state.update { it + reading }
    }

    override suspend fun getLatest(meterName: String): MeterReadingEntity? =
        state.value.filter { it.meterName == meterName }.maxByOrNull { it.recordedAt }

    override fun observeForMeter(meterName: String): Flow<List<MeterReadingEntity>> =
        state.map { list -> list.filter { it.meterName == meterName }.sortedByDescending { it.recordedAt } }

    override fun observeAll(): Flow<List<MeterReadingEntity>> =
        state.map { list -> list.sortedWith(compareBy<MeterReadingEntity> { it.meterName }.thenByDescending { it.recordedAt }) }

    override suspend fun insertAll(readings: List<MeterReadingEntity>) {
        state.update { it + readings }
    }

    override suspend fun deleteAll() {
        state.value = emptyList()
    }
}

/** Test seam: MeterRepository's constructor is `internal`, reachable from src/test via Kotlin friend paths. */
fun fakeMeterRepository(readings: List<MeterReadingEntity> = emptyList()): MeterRepository =
    MeterRepository(FakeMeterReadingDao(readings))

class FakeMeterSeedRepository(initialSeeded: Set<String> = emptySet()) : MeterSeedRepository {
    private val seeded = initialSeeded.toMutableSet()
    override suspend fun isSeeded(meterName: String): Boolean = meterName in seeded
    override suspend fun markSeeded(meterName: String) {
        seeded += meterName
    }
    override suspend fun getAllSeeded(): Set<String> = seeded.toSet()
}

class FakeWeeklyReviewSeedRepository(private var taskId: String? = null) : WeeklyReviewSeedRepository {
    override suspend fun isSeeded(): Boolean = taskId != null
    override suspend fun markSeeded(taskId: String) {
        this.taskId = taskId
    }
    override suspend fun seededTaskId(): String? = taskId
}

class FakeReminderRuleRepository(initial: Map<String, List<String>> = emptyMap()) : ReminderRuleRepository {
    private val offsetsByTaskId = initial.toMutableMap()

    override suspend fun getForTask(taskId: String): List<ReminderRuleEntity> =
        offsetsByTaskId[taskId].orEmpty().map { ReminderRuleEntity(id = "$taskId-$it", taskId = taskId, offsetIso = it) }

    override suspend fun setForTask(taskId: String, offsetIsos: List<String>) {
        offsetsByTaskId[taskId] = offsetIsos
    }
}

class FakeBackupSettingsRepository(
    initial: com.shuremind.data.repo.BackupSettings = com.shuremind.data.repo.BackupSettings()
) : com.shuremind.data.repo.BackupSettingsRepository {
    private val state = MutableStateFlow(initial)

    override val settings: Flow<com.shuremind.data.repo.BackupSettings> = state

    override suspend fun setAutoBackupEnabled(enabled: Boolean) {
        state.update { it.copy(autoBackupEnabled = enabled) }
    }

    override suspend fun setFolderUri(uri: String?) {
        state.update { it.copy(folderUri = uri) }
    }
}

class FakeBackupFileWriter : com.shuremind.data.repo.BackupFileWriter {
    override suspend fun writeToFolder(folderUri: String, json: String): Result<String> = Result.success("shuremind-backup-fake.json")
    override suspend fun writeSafetyExport(folderUri: String?, json: String): Result<Unit> = Result.success(Unit)
    override suspend fun writeToDocument(documentUri: String, json: String): Result<Unit> = Result.success(Unit)
    override suspend fun readDocument(documentUri: String): Result<String> = Result.success("")
    override fun hasPersistedPermission(folderUri: String): Boolean = true
}

class FakeBackupRepository : com.shuremind.data.backup.BackupRepository {
    override suspend fun buildSnapshot(): com.shuremind.data.backup.BackupSnapshot = com.shuremind.data.backup.BackupSnapshot(
        tasks = emptyList(),
        reminderRules = emptyList(),
        tags = emptyList(),
        taskTags = emptyList(),
        completions = emptyList(),
        meterReadings = emptyList(),
        settings = com.shuremind.data.backup.dto.ExportSettingsDto(
            quietHoursStart = "22:00",
            quietHoursEnd = "08:00",
            defaultAllDayTime = "09:00",
            currency = "EUR",
            defaultReminderOffsets = emptyMap(),
            snoozePresetsMinutes = emptyList(),
            defaultSnoozeDurationMinutes = 60L,
            weeklyReviewTaskId = null,
            seededMeterNames = emptySet(),
            uiLanguage = null
        )
    )
}

class FakeImportRepository : com.shuremind.data.backup.ImportRepository {
    var imported: com.shuremind.data.backup.dto.ExportFile? = null
        private set

    override suspend fun import(file: com.shuremind.data.backup.dto.ExportFile, now: Long) {
        imported = file
    }
}
