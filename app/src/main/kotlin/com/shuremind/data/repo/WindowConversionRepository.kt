package com.shuremind.data.repo

import com.shuremind.data.entity.TaskEntity
import java.time.LocalDate
import java.time.LocalTime

/**
 * D-25: WINDOW -> DEADLINE conversion ("date learned"). UI/ViewModels talk only to this repo,
 * never Task/ReminderRuleDao directly (matching M2/M3 patterns). Fakeable for ViewModel tests.
 */
interface WindowConversionRepository {

    /**
     * In one transaction: sets type=DEADLINE with the given due date/time, nulls every rec_*
     * field and window_hint, replaces this task's ReminderRules with [defaultReminderOffsets],
     * stamps updated_at/dirty, and recomputes next_fire_at. Returns the updated task, or null if
     * [taskId] no longer exists.
     */
    suspend fun convertWindowToDeadline(
        taskId: String,
        dueLocalDate: LocalDate,
        dueLocalTime: LocalTime?,
        defaultReminderOffsets: List<String>,
        now: Long = System.currentTimeMillis()
    ): TaskEntity?
}
