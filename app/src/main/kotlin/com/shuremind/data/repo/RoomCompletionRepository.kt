package com.shuremind.data.repo

import androidx.room.withTransaction
import com.shuremind.data.AppDatabase
import com.shuremind.data.dao.CompletionLogDao
import com.shuremind.data.dao.TaskDao
import com.shuremind.data.entity.CompletionLogEntity
import com.shuremind.data.entity.TaskEntity
import com.shuremind.engine.CompletionAction
import com.shuremind.engine.TaskStatus
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

internal class RoomCompletionRepository(
    private val database: AppDatabase,
    private val completionLogDao: CompletionLogDao,
    private val taskDao: TaskDao
) : CompletionRepository {

    override suspend fun recordCompletion(entry: CompletionLogEntity, now: Long) {
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

    override fun observeForTask(taskId: String): Flow<List<CompletionLogEntity>> = completionLogDao.observeForTask(taskId)

    override suspend fun getLastByAction(taskId: String, action: CompletionAction): CompletionLogEntity? =
        completionLogDao.getLastByAction(taskId, action)

    override suspend fun completeTask(task: TaskEntity, action: CompletionAction, now: Long, zone: ZoneId) {
        val nowZdt = Instant.ofEpochMilli(now).atZone(zone)
        val occurrenceInstant = task.nextFireAt?.let { Instant.ofEpochMilli(it).atZone(zone) } ?: nowZdt
        val occurrenceLocal = OCCURRENCE_FORMATTER.format(occurrenceInstant)
        database.withTransaction {
            completionLogDao.insert(
                CompletionLogEntity(
                    id = UUID.randomUUID().toString(),
                    taskId = task.id,
                    occurrenceLocal = occurrenceLocal,
                    action = action,
                    completedAt = now,
                    meterValue = null,
                    note = null
                )
            )
            val updated = if (task.type !in RECURRING_TYPES) {
                task.copy(status = TaskStatus.DONE, nextFireAt = null, snoozedUntil = null, updatedAt = now, dirty = 1)
            } else {
                val lastDoneAt = if (action == CompletionAction.DONE) {
                    nowZdt
                } else {
                    completionLogDao.getLastByAction(task.id, CompletionAction.DONE)?.completedAt
                        ?.let { Instant.ofEpochMilli(it).atZone(zone) }
                }
                val nextFireAt = NextFireAtCalculator.compute(task, zone, nowZdt, lastDoneAt)
                task.copy(nextFireAt = nextFireAt, snoozedUntil = null, updatedAt = now, dirty = 1)
            }
            taskDao.update(updated)
        }
    }

    private companion object {
        val OCCURRENCE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    }
}
