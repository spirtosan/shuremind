package com.shuremind.data.repo

import com.shuremind.data.dao.ReminderRuleDao
import com.shuremind.data.entity.ReminderRuleEntity
import java.util.UUID

internal class RoomReminderRuleRepository(private val reminderRuleDao: ReminderRuleDao) : ReminderRuleRepository {

    override suspend fun getForTask(taskId: String): List<ReminderRuleEntity> = reminderRuleDao.getForTask(taskId)

    override suspend fun setForTask(taskId: String, offsetIsos: List<String>) {
        reminderRuleDao.deleteForTask(taskId)
        offsetIsos.forEach { offset ->
            reminderRuleDao.upsert(ReminderRuleEntity(id = UUID.randomUUID().toString(), taskId = taskId, offsetIso = offset))
        }
    }
}
