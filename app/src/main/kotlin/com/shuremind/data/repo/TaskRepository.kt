package com.shuremind.data.repo

import com.shuremind.data.entity.TaskEntity
import com.shuremind.engine.TaskStatus
import com.shuremind.engine.TaskType
import kotlinx.coroutines.flow.Flow

/** UI/ViewModels talk only to this repo, never TaskDao directly (from M2 on). Fakeable for ViewModel unit tests. */
interface TaskRepository {

    suspend fun upsert(task: TaskEntity, now: Long = System.currentTimeMillis())

    suspend fun update(task: TaskEntity, now: Long = System.currentTimeMillis())

    suspend fun softDelete(id: String, now: Long = System.currentTimeMillis())

    suspend fun getById(id: String): TaskEntity?

    /** Snooze action (task 4, M2): only writes snoozed_until — alarms are M3. */
    suspend fun snooze(task: TaskEntity, until: Long, now: Long = System.currentTimeMillis())

    fun observeActive(excludedStatus: TaskStatus = TaskStatus.ARCHIVED): Flow<List<TaskEntity>>

    fun observeByType(type: TaskType): Flow<List<TaskEntity>>

    fun observeScheduled(): Flow<List<TaskEntity>>
}
