package com.shuremind.data.repo

import com.shuremind.data.entity.CompletionLogEntity
import com.shuremind.data.entity.TaskEntity
import com.shuremind.engine.CompletionAction
import kotlinx.coroutines.flow.Flow
import java.time.ZoneId

/** UI/ViewModels talk only to this repo, never CompletionLogDao directly (from M2 on). Fakeable for ViewModel unit tests. */
interface CompletionRepository {

    /**
     * Appends [entry] to the completion log. When [entry.meterValue] is set (a meter task was
     * completed with an odometer-style reading), also updates Task.last_done_meter atomically so
     * the two never drift apart.
     */
    suspend fun recordCompletion(entry: CompletionLogEntity, now: Long = System.currentTimeMillis())

    fun observeForTask(taskId: String): Flow<List<CompletionLogEntity>>

    suspend fun getLastByAction(taskId: String, action: CompletionAction = CompletionAction.DONE): CompletionLogEntity?

    /**
     * Main-list Done/Skip action (task 4, M2). Appends the log entry and advances the task:
     * one-shot types (EVENT/DEADLINE/SOMEDAY) become DONE with next_fire_at cleared; recurring
     * types ([RECURRING_TYPES]) stay ACTIVE and get next_fire_at recomputed via the engine —
     * COMPLETION-anchor recurrence only advances off a DONE action, never SKIPPED, per
     * DATA_MODEL.md's "next = last DONE completion + interval".
     */
    suspend fun completeTask(
        task: TaskEntity,
        action: CompletionAction,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault()
    )
}
