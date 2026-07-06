package com.shuremind.ui.detail

import com.shuremind.engine.RecurrenceAnchor
import com.shuremind.engine.RecurrenceFrequency
import com.shuremind.engine.TaskStatus
import com.shuremind.engine.TaskType
import java.time.DayOfWeek

/**
 * Editable form mirroring every nullable Task column (DATA_MODEL.md single-table design).
 * The edit screen shows/hides sections by [type]; switching type never clears other fields
 * (per task 6: "type is mutable ... keeps id/tags/log").
 */
data class TaskDetailUiState(
    val taskId: String = "",
    val isLoaded: Boolean = false,
    val title: String = "",
    val notes: String = "",
    val type: TaskType = TaskType.EVENT,
    val status: TaskStatus = TaskStatus.ACTIVE,
    val impact: Int = 1,
    val urgency: Int = 1,
    val estimatedCost: String = "",
    val dueLocalDate: String? = null,
    val dueLocalTime: String? = null,
    val notBefore: String? = null,
    val recFreq: RecurrenceFrequency = RecurrenceFrequency.DAILY,
    val recInterval: String = "1",
    val recAnchor: RecurrenceAnchor = RecurrenceAnchor.CALENDAR,
    val recDaysOfWeek: Set<DayOfWeek> = emptySet(),
    val recDayOfMonth: String = "",
    val recTimesOfDay: List<String> = emptyList(),
    val recEndDate: String? = null,
    val nagIntervalHours: String = "",
    val stockQty: String = "",
    val dosePerIntake: String = "",
    val restockLeadDays: String = "",
    val stockRecordedAt: String? = null,
    /** D-26: computed at read time, shown alongside the editable stock fields — never edited directly. */
    val remainingStock: Double? = null,
    val runOutDate: String? = null,
    val boughtQuantity: String = "",
    val showRestockDialog: Boolean = false,
    val meterName: String = "",
    val meterInterval: String = "",
    val lastDoneMeter: String = "",
    val windowHint: String = "",
    /** D-25: pending "date learned" input for WINDOW -> DEADLINE conversion; unrelated to dueLocalDate/Time. */
    val dateLearnedDate: String? = null,
    val dateLearnedTime: String? = null,
    val reminderOffsets: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    /** D-39 tag picker: every existing tag name in the system, shown as toggle chips so the user never retypes one. */
    val allTags: List<String> = emptyList(),
    val tagInput: String = "",
    val justSaved: Boolean = false,
    val justDeleted: Boolean = false
) {
    val canSave: Boolean get() = title.isNotBlank()
}
