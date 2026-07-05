package com.shuremind.data.repo

import com.shuremind.data.dao.TaskDao
import com.shuremind.data.entity.TaskEntity
import com.shuremind.engine.TaskStatus
import com.shuremind.engine.TaskType
import kotlinx.coroutines.flow.Flow

/** UI/ViewModels talk only to this repo, never TaskDao directly (from M2 on). */
class TaskRepository(private val taskDao: TaskDao) {

    suspend fun upsert(task: TaskEntity, now: Long = System.currentTimeMillis()) {
        taskDao.upsert(task.copy(updatedAt = now, dirty = 1))
    }

    suspend fun update(task: TaskEntity, now: Long = System.currentTimeMillis()) {
        taskDao.update(task.copy(updatedAt = now, dirty = 1))
    }

    suspend fun softDelete(id: String, now: Long = System.currentTimeMillis()) {
        val task = taskDao.getById(id) ?: return
        taskDao.update(task.copy(deletedAt = now, updatedAt = now, dirty = 1))
    }

    suspend fun getById(id: String): TaskEntity? = taskDao.getById(id)

    fun observeActive(excludedStatus: TaskStatus = TaskStatus.ARCHIVED): Flow<List<TaskEntity>> =
        taskDao.observeActive(excludedStatus)

    fun observeByType(type: TaskType): Flow<List<TaskEntity>> = taskDao.observeByType(type)

    fun observeScheduled(): Flow<List<TaskEntity>> = taskDao.observeScheduled()
}
