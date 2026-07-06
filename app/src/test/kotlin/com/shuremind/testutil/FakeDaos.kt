package com.shuremind.testutil

import com.shuremind.data.dao.CompletionLogDao
import com.shuremind.data.dao.ReminderRuleDao
import com.shuremind.data.dao.TagDao
import com.shuremind.data.dao.TaskDao
import com.shuremind.data.dao.TaskTagDao
import com.shuremind.data.entity.CompletionLogEntity
import com.shuremind.data.entity.ReminderRuleEntity
import com.shuremind.data.entity.TagEntity
import com.shuremind.data.entity.TaskEntity
import com.shuremind.data.entity.TaskTagEntity
import com.shuremind.engine.CompletionAction
import com.shuremind.engine.TaskStatus
import com.shuremind.engine.TaskType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * In-memory Dao fakes for M5 export/import tests — same pattern as [FakeMeterReadingDao]. Let
 * [com.shuremind.data.backup.RoomBackupRepository]/[com.shuremind.data.backup.RoomImportRepository]
 * be exercised in plain JVM unit tests via [com.shuremind.data.repo.TransactionRunner], no real Room needed.
 */
class FakeTaskDao(initial: List<TaskEntity> = emptyList()) : TaskDao {
    private val state = MutableStateFlow(initial)
    val current: List<TaskEntity> get() = state.value

    override suspend fun upsert(task: TaskEntity) {
        state.update { list -> list.filterNot { it.id == task.id } + task }
    }

    override suspend fun insertAll(tasks: List<TaskEntity>) {
        state.update { it + tasks }
    }

    override suspend fun update(task: TaskEntity) {
        state.update { list -> list.map { if (it.id == task.id) task else it } }
    }

    override suspend fun getById(id: String): TaskEntity? = state.value.find { it.id == id }

    override fun observeActive(excludedStatus: TaskStatus): Flow<List<TaskEntity>> =
        state.map { list -> list.filter { it.deletedAt == null && it.status != excludedStatus } }

    override fun observeByType(type: TaskType): Flow<List<TaskEntity>> =
        state.map { list -> list.filter { it.deletedAt == null && it.type == type } }

    override fun observeScheduled(): Flow<List<TaskEntity>> =
        state.map { list -> list.filter { it.deletedAt == null && it.nextFireAt != null }.sortedBy { it.nextFireAt } }

    override suspend fun hardDelete(id: String) {
        state.update { list -> list.filterNot { it.id == id } }
    }

    override suspend fun getAllForExport(): List<TaskEntity> = state.value

    override suspend fun deleteAll() {
        state.value = emptyList()
    }
}

class FakeReminderRuleDao(initial: List<ReminderRuleEntity> = emptyList()) : ReminderRuleDao {
    private val state = MutableStateFlow(initial)

    override suspend fun upsert(rule: ReminderRuleEntity) {
        state.update { list -> list.filterNot { it.id == rule.id } + rule }
    }

    override suspend fun insertAll(rules: List<ReminderRuleEntity>) {
        state.update { it + rules }
    }

    override suspend fun getForTask(taskId: String): List<ReminderRuleEntity> = state.value.filter { it.taskId == taskId }

    override suspend fun deleteForTask(taskId: String) {
        state.update { list -> list.filterNot { it.taskId == taskId } }
    }

    override suspend fun delete(rule: ReminderRuleEntity) {
        state.update { list -> list.filterNot { it.id == rule.id } }
    }

    override suspend fun getAllForExport(): List<ReminderRuleEntity> = state.value

    override suspend fun deleteAll() {
        state.value = emptyList()
    }
}

class FakeTagDao(initial: List<TagEntity> = emptyList()) : TagDao {
    private val state = MutableStateFlow(initial)

    override suspend fun upsert(tag: TagEntity) {
        state.update { list -> list.filterNot { it.id == tag.id } + tag }
    }

    override suspend fun insertAll(tags: List<TagEntity>) {
        state.update { it + tags }
    }

    override fun observeAll(): Flow<List<TagEntity>> = state.map { it.sortedBy { tag -> tag.name } }

    override suspend fun getByName(name: String): TagEntity? = state.value.find { it.name == name }

    override suspend fun deleteAll() {
        state.value = emptyList()
    }
}

class FakeTaskTagDao(initial: List<TaskTagEntity> = emptyList()) : TaskTagDao {
    private val state = MutableStateFlow(initial)

    override suspend fun upsert(crossRef: TaskTagEntity) {
        state.update { list -> list.filterNot { it.taskId == crossRef.taskId && it.tagId == crossRef.tagId } + crossRef }
    }

    override suspend fun insertAll(crossRefs: List<TaskTagEntity>) {
        state.update { it + crossRefs }
    }

    override suspend fun delete(crossRef: TaskTagEntity) {
        state.update { list -> list.filterNot { it.taskId == crossRef.taskId && it.tagId == crossRef.tagId } }
    }

    override suspend fun getTagIdsForTask(taskId: String): List<String> = state.value.filter { it.taskId == taskId }.map { it.tagId }

    override suspend fun getTaskIdsForTag(tagId: String): List<String> = state.value.filter { it.tagId == tagId }.map { it.taskId }

    override fun observeAll(): Flow<List<TaskTagEntity>> = state

    override suspend fun deleteAll() {
        state.value = emptyList()
    }
}

class FakeCompletionLogDao(initial: List<CompletionLogEntity> = emptyList()) : CompletionLogDao {
    private val state = MutableStateFlow(initial)

    override suspend fun insert(entry: CompletionLogEntity) {
        state.update { it + entry }
    }

    override suspend fun insertAll(entries: List<CompletionLogEntity>) {
        state.update { it + entries }
    }

    override fun observeForTask(taskId: String): Flow<List<CompletionLogEntity>> =
        state.map { list -> list.filter { it.taskId == taskId }.sortedByDescending { it.completedAt } }

    override suspend fun getLastByAction(taskId: String, action: CompletionAction): CompletionLogEntity? =
        state.value.filter { it.taskId == taskId && it.action == action }.maxByOrNull { it.completedAt }

    override suspend fun getAllForExport(): List<CompletionLogEntity> = state.value

    override suspend fun deleteAll() {
        state.value = emptyList()
    }
}
