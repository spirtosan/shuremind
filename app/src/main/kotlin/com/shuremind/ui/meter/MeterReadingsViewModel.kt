package com.shuremind.ui.meter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shuremind.data.entity.MeterReadingEntity
import com.shuremind.data.entity.TaskEntity
import com.shuremind.data.repo.MeterRepository
import com.shuremind.data.repo.MeterSeedRepository
import com.shuremind.data.repo.NextFireAtCalculator
import com.shuremind.data.repo.SettingsRepository
import com.shuremind.data.repo.TaskRepository
import com.shuremind.data.repo.toCsv
import com.shuremind.engine.RecurrenceAnchor
import com.shuremind.engine.RecurrenceFrequency
import com.shuremind.engine.TaskStatus
import com.shuremind.engine.TaskType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

/** Backs the Meter readings screen (D-27): grouped readings, add-reading form, monthly-task seeding. */
class MeterReadingsViewModel(
    private val meterRepository: MeterRepository,
    private val meterSeedRepository: MeterSeedRepository,
    private val taskRepository: TaskRepository,
    private val settingsRepository: SettingsRepository,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val nowProvider: () -> Long = System::currentTimeMillis
) : ViewModel() {

    val uiState: StateFlow<MeterReadingsUiState> = combine(
        meterRepository.observeAll(),
        taskRepository.observeActive()
    ) { readings, tasks -> buildUiState(readings, tasks) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, MeterReadingsUiState())

    private val _input = MutableStateFlow(MeterReadingInputState())
    val input: StateFlow<MeterReadingInputState> = _input.asStateFlow()

    init {
        // Prefill the meter-name field once we know the default, without clobbering user typing.
        viewModelScope.launch {
            uiState.collect { state ->
                if (_input.value.meterName.isBlank() && state.defaultMeterName.isNotBlank()) {
                    _input.update { it.copy(meterName = state.defaultMeterName) }
                }
            }
        }
    }

    private fun buildUiState(readings: List<MeterReadingEntity>, tasks: List<TaskEntity>): MeterReadingsUiState {
        val byMeter = readings.groupBy { it.meterName } // DAO already orders meter_name ASC, recorded_at DESC
        val tasksByMeter = tasks.filter { it.meterName != null }.groupBy { it.meterName!! }
        val groups = byMeter.keys.sorted().map { name ->
            val meterReadings = byMeter.getValue(name)
            val latestValue = meterReadings.firstOrNull()?.value
            val joinedTasks = tasksByMeter[name].orEmpty().map { task ->
                val lastDone = task.lastDoneMeter
                val delta = if (latestValue != null && lastDone != null) latestValue - lastDone else null
                MeterTaskDelta(task, delta)
            }
            MeterGroup(meterName = name, readings = meterReadings, joinedTasks = joinedTasks)
        }
        val defaultMeterName = groups.maxByOrNull { it.readings.firstOrNull()?.recordedAt ?: 0L }?.meterName ?: ""
        return MeterReadingsUiState(groups = groups, defaultMeterName = defaultMeterName)
    }

    fun setMeterNameInput(value: String) = _input.update { it.copy(meterName = value) }
    fun setValueInput(value: String) = _input.update { it.copy(value = value) }

    /** [seedTaskTitle] is pre-localized by the caller (Composable) so this ViewModel never touches Android resources. */
    fun addReading(seedTaskTitle: String) {
        val state = _input.value
        val meterName = state.meterName.trim()
        val value = state.value.toDoubleOrNull()
        if (meterName.isBlank() || value == null) return
        viewModelScope.launch {
            val hadNoReadings = meterRepository.observeForMeter(meterName).first().isEmpty()
            val now = nowProvider()
            meterRepository.record(MeterReadingEntity(id = UUID.randomUUID().toString(), meterName = meterName, value = value, recordedAt = now))
            if (hadNoReadings && !meterSeedRepository.isSeeded(meterName)) {
                seedMonthlyTask(meterName, seedTaskTitle, now)
                meterSeedRepository.markSeeded(meterName)
            }
            _input.value = MeterReadingInputState(meterName = meterName)
        }
    }

    /**
     * D-27: first-ever reading for [meterName] seeds a RECURRING CALENDAR monthly reminder — day of
     * month defaults to today's, time to the user's configured default_all_day_time (not the engine
     * constant, so a user override in Settings is honored here).
     */
    private suspend fun seedMonthlyTask(meterName: String, title: String, now: Long) {
        val nowZdt = Instant.ofEpochMilli(now).atZone(zone)
        val defaultTime = settingsRepository.settings.first().defaultAllDayTime
        val entity = TaskEntity(
            id = UUID.randomUUID().toString(),
            title = title,
            notes = null,
            type = TaskType.RECURRING,
            status = TaskStatus.ACTIVE,
            impact = 1,
            urgency = 1,
            estimatedCost = null,
            dueLocalDate = null,
            dueLocalTime = null,
            notBefore = null,
            recFreq = RecurrenceFrequency.MONTHLY,
            recInterval = 1,
            recAnchor = RecurrenceAnchor.CALENDAR,
            recDaysOfWeek = null,
            recDayOfMonth = nowZdt.dayOfMonth,
            recTimesOfDay = listOf(defaultTime).toCsv(),
            recEndDate = null,
            nagIntervalHours = null,
            stockQty = null,
            dosePerIntake = null,
            restockLeadDays = null,
            stockRecordedAt = null,
            meterName = null,
            meterInterval = null,
            lastDoneMeter = null,
            windowHint = null,
            snoozedUntil = null,
            nextFireAt = null,
            createdAt = now,
            updatedAt = now,
            deletedAt = null,
            dirty = 1
        )
        val withNextFire = entity.copy(nextFireAt = NextFireAtCalculator.compute(entity, zone, nowZdt, lastDoneAt = null))
        taskRepository.upsert(withNextFire, now)
    }
}
