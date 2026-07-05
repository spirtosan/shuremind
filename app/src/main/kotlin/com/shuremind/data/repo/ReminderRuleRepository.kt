package com.shuremind.data.repo

import com.shuremind.data.dao.ReminderRuleDao
import com.shuremind.data.entity.ReminderRuleEntity
import java.util.UUID

/** UI/ViewModels talk only to this repo, never ReminderRuleDao directly (from M2 on). */
class ReminderRuleRepository internal constructor(private val reminderRuleDao: ReminderRuleDao) {

    suspend fun getForTask(taskId: String): List<ReminderRuleEntity> = reminderRuleDao.getForTask(taskId)

    /** Replaces all lead-time reminders for [taskId] with [offsetIsos] (e.g. "P14D", "P1D", "PT2H"). */
    suspend fun setForTask(taskId: String, offsetIsos: List<String>) {
        reminderRuleDao.deleteForTask(taskId)
        offsetIsos.forEach { offset ->
            reminderRuleDao.upsert(ReminderRuleEntity(id = UUID.randomUUID().toString(), taskId = taskId, offsetIso = offset))
        }
    }
}
