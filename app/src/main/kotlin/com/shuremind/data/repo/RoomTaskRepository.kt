package com.shuremind.data.repo

import com.shuremind.data.dao.TaskDao
import com.shuremind.data.entity.TaskEntity
import com.shuremind.engine.TaskStatus
import com.shuremind.engine.TaskType
import kotlinx.coroutines.flow.Flow

internal class RoomTaskRepository(
    private val taskDao: TaskDao,
    private val scheduleChangeNotifier: ScheduleChangeNotifier = ScheduleChangeNotifier.NONE
) : TaskRepository {

    override suspend fun upsert(task: TaskEntity, now: Long) {
        taskDao.upsert(task.copy(updatedAt = now, dirty = 1))
        scheduleChangeNotifier.onScheduleChanged()
    }

    override suspend fun update(task: TaskEntity, now: Long) {
        taskDao.update(task.copy(updatedAt = now, dirty = 1))
        scheduleChangeNotifier.onScheduleChanged()
    }

    override suspend fun softDelete(id: String, now: Long) {
        val task = taskDao.getById(id) ?: return
        taskDao.update(task.copy(deletedAt = now, updatedAt = now, dirty = 1))
        scheduleChangeNotifier.onScheduleChanged()
    }

    override suspend fun getById(id: String): TaskEntity? = taskDao.getById(id)

    override suspend fun snooze(task: TaskEntity, until: Long, now: Long) {
        taskDao.update(task.copy(snoozedUntil = until, updatedAt = now, dirty = 1))
        scheduleChangeNotifier.onScheduleChanged()
    }

    override fun observeActive(excludedStatus: TaskStatus): Flow<List<TaskEntity>> =
        taskDao.observeActive(excludedStatus)

    override fun observeByType(type: TaskType): Flow<List<TaskEntity>> = taskDao.observeByType(type)

    override fun observeScheduled(): Flow<List<TaskEntity>> = taskDao.observeScheduled()
}
