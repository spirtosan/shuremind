package com.shuremind.testutil

import com.shuremind.data.entity.CompletionLogEntity
import com.shuremind.data.entity.TagEntity
import com.shuremind.data.entity.TaskEntity
import com.shuremind.data.repo.AppSettings
import com.shuremind.data.repo.CompletionRepository
import com.shuremind.data.repo.SettingsRepository
import com.shuremind.data.repo.TagRepository
import com.shuremind.data.repo.TaskRepository
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
    deletedAt: Long? = null
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
    meterName = null,
    meterInterval = null,
    lastDoneMeter = null,
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
    override suspend fun recordCompletion(entry: CompletionLogEntity, now: Long) = Unit
    override fun observeForTask(taskId: String): Flow<List<CompletionLogEntity>> = flowOf(emptyList())
    override suspend fun getLastByAction(taskId: String, action: CompletionAction): CompletionLogEntity? = null
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
}
