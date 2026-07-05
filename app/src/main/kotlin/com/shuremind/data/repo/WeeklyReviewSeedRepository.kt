package com.shuremind.data.repo

/**
 * D-28: idempotent "seed the weekly review reminder task on first run" flag. The stored task id
 * also lets notification routing recognize a fired occurrence as the weekly-review task (so its
 * notification tap opens the review screen instead of task detail).
 */
interface WeeklyReviewSeedRepository {

    suspend fun isSeeded(): Boolean

    suspend fun markSeeded(taskId: String)

    suspend fun seededTaskId(): String?
}
