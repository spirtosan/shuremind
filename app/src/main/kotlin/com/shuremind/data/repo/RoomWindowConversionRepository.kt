package com.shuremind.data.repo

import androidx.room.withTransaction
import com.shuremind.data.AppDatabase
import com.shuremind.data.dao.ReminderRuleDao
import com.shuremind.data.dao.TaskDao
import com.shuremind.data.entity.ReminderRuleEntity
import com.shuremind.data.entity.TaskEntity
import com.shuremind.engine.TaskType
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

internal class RoomWindowConversionRepository(
    private val database: AppDatabase,
    private val taskDao: TaskDao,
    private val reminderRuleDao: ReminderRuleDao,
    private val scheduleChangeNotifier: ScheduleChangeNotifier = ScheduleChangeNotifier.NONE,
    private val zone: ZoneId = ZoneId.systemDefault()
) : WindowConversionRepository {

    override suspend fun convertWindowToDeadline(
        taskId: String,
        dueLocalDate: LocalDate,
        dueLocalTime: LocalTime?,
        defaultReminderOffsets: List<String>,
        now: Long
    ): TaskEntity? {
        val updated: TaskEntity? = database.withTransaction {
            val existing = taskDao.getById(taskId) ?: return@withTransaction null
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
            val withNextFire = converted.copy(
                nextFireAt = NextFireAtCalculator.compute(converted, zone, nowZdt, lastDoneAt = null)
            )
            taskDao.update(withNextFire)
            reminderRuleDao.deleteForTask(taskId)
            defaultReminderOffsets.forEach { offset ->
                reminderRuleDao.upsert(ReminderRuleEntity(id = UUID.randomUUID().toString(), taskId = taskId, offsetIso = offset))
            }
            withNextFire
        }
        if (updated != null) scheduleChangeNotifier.onScheduleChanged()
        return updated
    }
}
