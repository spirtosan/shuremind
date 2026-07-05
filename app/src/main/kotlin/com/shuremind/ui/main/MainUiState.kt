package com.shuremind.ui.main

import com.shuremind.data.entity.TagEntity
import com.shuremind.data.entity.TaskEntity
import com.shuremind.engine.TaskType

/** One row in the main list, pre-joined with its tags and read-time priority score. */
data class TaskListItem(
    val task: TaskEntity,
    val tags: List<TagEntity>,
    val score: Int?,
    val isSnoozedActive: Boolean
)

data class MainUiState(
    val overdue: List<TaskListItem> = emptyList(),
    val today: List<TaskListItem> = emptyList(),
    val upcoming: List<TaskListItem> = emptyList(),
    val someday: List<TaskListItem> = emptyList(),
    val allTags: List<TagEntity> = emptyList(),
    val selectedTagId: String? = null,
    val snoozePresetsMinutes: List<Long> = emptyList()
)

/** Quick-capture bar state (task 3, M2): plain capture is title + Save, everything else optional. */
data class QuickCaptureState(
    val title: String = "",
    val expanded: Boolean = false,
    val type: TaskType = TaskType.EVENT,
    val impact: Int = 1,
    val urgency: Int = 1,
    val tags: List<String> = emptyList(),
    val tagInput: String = "",
    val dueLocalDate: String? = null,
    val dueLocalTime: String? = null,
    val estimatedCost: String = ""
) {
    val canSave: Boolean get() = title.isNotBlank()
}
