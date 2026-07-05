package com.shuremind.data.repo

import androidx.room.withTransaction
import com.shuremind.data.AppDatabase
import com.shuremind.data.dao.CompletionLogDao
import com.shuremind.data.dao.TaskDao
import com.shuremind.data.entity.CompletionLogEntity
import com.shuremind.engine.CompletionAction
import kotlinx.coroutines.flow.Flow

/** UI/ViewModels talk only to this repo, never CompletionLogDao directly (from M2 on). */
class CompletionRepository(
    private val database: AppDatabase,
    private val completionLogDao: CompletionLogDao,
    private val taskDao: TaskDao
) {

    /**
     * Appends [entry] to the completion log. When [entry.meterValue] is set (a meter task was
     * completed with an odometer-style reading), also updates Task.last_done_meter in the same
     * transaction so the two never drift apart.
     */
    suspend fun recordCompletion(entry: CompletionLogEntity, now: Long = System.currentTimeMillis()) {
        val meterValue = entry.meterValue
        if (meterValue == null) {
            completionLogDao.insert(entry)
            return
        }
        database.withTransaction {
            completionLogDao.insert(entry)
            val task = taskDao.getById(entry.taskId) ?: return@withTransaction
            taskDao.update(task.copy(lastDoneMeter = meterValue, updatedAt = now, dirty = 1))
        }
    }

    fun observeForTask(taskId: String): Flow<List<CompletionLogEntity>> = completionLogDao.observeForTask(taskId)

    suspend fun getLastByAction(taskId: String, action: CompletionAction = CompletionAction.DONE): CompletionLogEntity? =
        completionLogDao.getLastByAction(taskId, action)
}
