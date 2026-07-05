package com.shuremind.data.repo

import com.shuremind.data.entity.ReminderRuleEntity

/** UI/ViewModels talk only to this repo, never ReminderRuleDao directly (from M2 on). Fakeable for use-case/ViewModel unit tests. */
interface ReminderRuleRepository {

    suspend fun getForTask(taskId: String): List<ReminderRuleEntity>

    /** Replaces all lead-time reminders for [taskId] with [offsetIsos] (e.g. "P14D", "P1D", "PT2H"). */
    suspend fun setForTask(taskId: String, offsetIsos: List<String>)
}
