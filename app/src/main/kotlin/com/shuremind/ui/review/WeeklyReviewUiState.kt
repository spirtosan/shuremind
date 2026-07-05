package com.shuremind.ui.review

import com.shuremind.data.entity.TaskEntity

/** D-28: the three weekly-review sections. */
data class WeeklyReviewUiState(
    val someday: List<TaskEntity> = emptyList(),
    val stale: List<TaskEntity> = emptyList(),
    val windows: List<TaskEntity> = emptyList(),
    val snoozePresetsMinutes: List<Long> = emptyList()
)
