package com.shuremind.ui.meter

import com.shuremind.data.entity.MeterReadingEntity
import com.shuremind.data.entity.TaskEntity

/** One RECURRING task joined on a meter_name, with its delta since last_done_meter (null if never done). */
data class MeterTaskDelta(val task: TaskEntity, val deltaSinceLastDone: Double?)

/** One meter_name's readings (newest first) plus the tasks that track it. */
data class MeterGroup(
    val meterName: String,
    val readings: List<MeterReadingEntity>,
    val joinedTasks: List<MeterTaskDelta>
)

data class MeterReadingsUiState(
    val groups: List<MeterGroup> = emptyList(),
    /** D-27: "defaults to the only/most recent meter" — the meter_name with the newest reading. */
    val defaultMeterName: String = ""
)

/** Add-reading form state (task 3, M4): meter_name defaults to the only/most recent meter. */
data class MeterReadingInputState(
    val meterName: String = "",
    val value: String = ""
) {
    val canSave: Boolean get() = meterName.isNotBlank() && value.toDoubleOrNull() != null
}
