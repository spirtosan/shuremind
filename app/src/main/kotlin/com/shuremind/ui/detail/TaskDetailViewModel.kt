package com.shuremind.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shuremind.data.entity.TaskEntity
import com.shuremind.data.repo.CompletionRepository
import com.shuremind.data.repo.NextFireAtCalculator
import com.shuremind.data.repo.ReminderRuleRepository
import com.shuremind.data.repo.SettingsRepository
import com.shuremind.data.repo.TagRepository
import com.shuremind.data.repo.TaskRepository
import com.shuremind.data.repo.WindowConversionRepository
import com.shuremind.data.repo.parseDaysOfWeekCsv
import com.shuremind.data.repo.parseTimesOfDayCsv
import com.shuremind.data.repo.toCsv
import com.shuremind.engine.CompletionAction
import com.shuremind.engine.ConsumableEngine
import com.shuremind.engine.RecurrenceAnchor
import com.shuremind.engine.RecurrenceFrequency
import com.shuremind.engine.TaskType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Backs the task detail/edit screen (task 6, M2): loads one task, edits every field, saves with next_fire_at recompute. */
class TaskDetailViewModel(
    private val taskRepository: TaskRepository,
    private val tagRepository: TagRepository,
    private val reminderRuleRepository: ReminderRuleRepository,
    private val completionRepository: CompletionRepository,
    private val settingsRepository: SettingsRepository,
    private val windowConversionRepository: WindowConversionRepository,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val nowProvider: () -> Long = System::currentTimeMillis
) : ViewModel() {

    private val _uiState = MutableStateFlow(TaskDetailUiState())
    val uiState: StateFlow<TaskDetailUiState> = _uiState.asStateFlow()

    fun load(taskId: String, autoOpenRestock: Boolean = false) {
        viewModelScope.launch {
            val task = taskRepository.getById(taskId) ?: return@launch
            val tags = tagRepository.getTagsForTask(taskId).map { it.name }
            var reminderOffsets = reminderRuleRepository.getForTask(taskId).map { it.offsetIso }
            if (reminderOffsets.isEmpty() && task.type in REMINDER_RULE_TYPES) {
                reminderOffsets = settingsRepository.settings.first().defaultReminderOffsets[task.type].orEmpty()
            }
            val (remainingStock, runOutDate) = consumableDisplayFor(task)
            _uiState.value = TaskDetailUiState(
                taskId = task.id,
                isLoaded = true,
                title = task.title,
                notes = task.notes ?: "",
                type = task.type,
                status = task.status,
                impact = task.impact,
                urgency = task.urgency,
                estimatedCost = task.estimatedCost?.toString() ?: "",
                dueLocalDate = task.dueLocalDate,
                dueLocalTime = task.dueLocalTime,
                notBefore = task.notBefore,
                recFreq = task.recFreq ?: RecurrenceFrequency.DAILY,
                recInterval = task.recInterval.toString(),
                recAnchor = task.recAnchor ?: RecurrenceAnchor.CALENDAR,
                recDaysOfWeek = task.recDaysOfWeek.parseDaysOfWeekCsv(),
                recDayOfMonth = task.recDayOfMonth?.toString() ?: "",
                recTimesOfDay = task.recTimesOfDay.parseTimesOfDayCsv().map { it.toString() },
                recEndDate = task.recEndDate,
                nagIntervalHours = task.nagIntervalHours?.toString() ?: "",
                stockQty = task.stockQty?.toString() ?: "",
                dosePerIntake = task.dosePerIntake?.toString() ?: "",
                restockLeadDays = task.restockLeadDays?.toString() ?: "",
                stockRecordedAt = task.stockRecordedAt,
                remainingStock = remainingStock,
                runOutDate = runOutDate,
                meterName = task.meterName ?: "",
                meterInterval = task.meterInterval?.toString() ?: "",
                lastDoneMeter = task.lastDoneMeter?.toString() ?: "",
                windowHint = task.windowHint ?: "",
                reminderOffsets = reminderOffsets,
                tags = tags,
                showRestockDialog = autoOpenRestock && task.type == TaskType.CONSUMABLE
            )
        }
    }

    private fun consumableDisplayFor(task: TaskEntity): Pair<Double?, String?> {
        val dosePerIntake = task.dosePerIntake
        val stockQty = task.stockQty
        if (task.type != TaskType.CONSUMABLE || dosePerIntake == null || stockQty == null) return null to null
        val today = Instant.ofEpochMilli(nowProvider()).atZone(zone).toLocalDate()
        val stockRecordedAt = task.stockRecordedAt?.let(LocalDate::parse) ?: today
        val intakesPerDay = task.recTimesOfDay.parseTimesOfDayCsv().size.coerceAtLeast(1)
        val remaining = ConsumableEngine.remainingStock(stockRecordedAt, stockQty, dosePerIntake, intakesPerDay, today)
        val runOutDate = ConsumableEngine.computeRunOutDate(stockRecordedAt, stockQty, dosePerIntake, intakesPerDay)
        return remaining to runOutDate.toString()
    }

    fun setTitle(value: String) = update { it.copy(title = value) }
    fun setNotes(value: String) = update { it.copy(notes = value) }
    fun setType(value: TaskType) = update { it.copy(type = value) }
    fun setImpact(value: Int) = update { it.copy(impact = value) }
    fun setUrgency(value: Int) = update { it.copy(urgency = value) }
    fun setEstimatedCost(value: String) = update { it.copy(estimatedCost = value) }
    fun setDueLocalDate(value: String?) = update { it.copy(dueLocalDate = value) }
    fun setDueLocalTime(value: String?) = update { it.copy(dueLocalTime = value) }
    fun setNotBefore(value: String?) = update { it.copy(notBefore = value) }
    fun setRecFreq(value: RecurrenceFrequency) = update { it.copy(recFreq = value) }
    fun setRecInterval(value: String) = update { it.copy(recInterval = value) }
    fun setRecAnchor(value: RecurrenceAnchor) = update { it.copy(recAnchor = value) }
    fun toggleRecDayOfWeek(day: DayOfWeek) = update {
        it.copy(recDaysOfWeek = if (day in it.recDaysOfWeek) it.recDaysOfWeek - day else it.recDaysOfWeek + day)
    }
    fun setRecDayOfMonth(value: String) = update { it.copy(recDayOfMonth = value) }
    fun setRecEndDate(value: String?) = update { it.copy(recEndDate = value) }
    fun addRecTimeOfDay(time: String) = update { it.copy(recTimesOfDay = (it.recTimesOfDay + time).distinct().sorted()) }
    fun removeRecTimeOfDay(time: String) = update { it.copy(recTimesOfDay = it.recTimesOfDay - time) }
    fun setNagIntervalHours(value: String) = update { it.copy(nagIntervalHours = value) }
    fun setStockQty(value: String) = update { it.copy(stockQty = value) }
    fun setDosePerIntake(value: String) = update { it.copy(dosePerIntake = value) }
    fun setRestockLeadDays(value: String) = update { it.copy(restockLeadDays = value) }
    fun setMeterName(value: String) = update { it.copy(meterName = value) }
    fun setMeterInterval(value: String) = update { it.copy(meterInterval = value) }
    fun setLastDoneMeter(value: String) = update { it.copy(lastDoneMeter = value) }
    fun setWindowHint(value: String) = update { it.copy(windowHint = value) }
    fun setTagInput(value: String) = update { it.copy(tagInput = value) }
    fun addTag() {
        val name = _uiState.value.tagInput.trim()
        if (name.isEmpty()) return
        update { it.copy(tags = (it.tags + name).distinct(), tagInput = "") }
    }
    fun removeTag(name: String) = update { it.copy(tags = it.tags - name) }
    fun addReminderOffset(offsetIso: String) = update { it.copy(reminderOffsets = (it.reminderOffsets + offsetIso).distinct()) }
    fun removeReminderOffset(offsetIso: String) = update { it.copy(reminderOffsets = it.reminderOffsets - offsetIso) }

    // --- D-25: WINDOW -> DEADLINE "date learned" ---
    fun setDateLearnedDate(value: String?) = update { it.copy(dateLearnedDate = value) }
    fun setDateLearnedTime(value: String?) = update { it.copy(dateLearnedTime = value) }

    fun convertWindowToDeadline() {
        val state = _uiState.value
        val dueLocalDate = state.dateLearnedDate ?: return
        viewModelScope.launch {
            val defaultOffsets = settingsRepository.settings.first().defaultReminderOffsets[TaskType.DEADLINE].orEmpty()
            val updated = windowConversionRepository.convertWindowToDeadline(
                taskId = state.taskId,
                dueLocalDate = LocalDate.parse(dueLocalDate),
                dueLocalTime = state.dateLearnedTime?.let(LocalTime::parse),
                defaultReminderOffsets = defaultOffsets,
                now = nowProvider()
            )
            if (updated != null) load(state.taskId)
        }
    }

    // --- D-26: CONSUMABLE restock ---
    fun setBoughtQuantity(value: String) = update { it.copy(boughtQuantity = value) }
    fun openRestockDialog() = update { it.copy(showRestockDialog = true) }
    fun dismissRestockDialog() = update { it.copy(showRestockDialog = false, boughtQuantity = "") }

    fun confirmRestock() {
        val state = _uiState.value
        val bought = state.boughtQuantity.toDoubleOrNull() ?: return
        viewModelScope.launch {
            val existing = taskRepository.getById(state.taskId) ?: return@launch
            val now = nowProvider()
            val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
            val (remaining, _) = consumableDisplayFor(existing)
            var updated = existing.copy(
                stockQty = (remaining ?: 0.0) + bought,
                stockRecordedAt = today.toString(),
                updatedAt = now,
                dirty = 1
            )
            val nowZdt = Instant.ofEpochMilli(now).atZone(zone)
            updated = updated.copy(nextFireAt = NextFireAtCalculator.compute(updated, zone, nowZdt, lastDoneAt = null))
            taskRepository.update(updated, now)
            load(state.taskId)
        }
    }

    private fun update(block: (TaskDetailUiState) -> TaskDetailUiState) {
        _uiState.value = block(_uiState.value)
    }

    fun save() {
        val state = _uiState.value
        if (!state.canSave) return
        viewModelScope.launch {
            val existing = taskRepository.getById(state.taskId) ?: return@launch
            val now = nowProvider()
            var updated = existing.copy(
                title = state.title.trim(),
                notes = state.notes.ifBlank { null },
                type = state.type,
                impact = state.impact,
                urgency = state.urgency,
                estimatedCost = state.estimatedCost.toDoubleOrNull(),
                dueLocalDate = state.dueLocalDate,
                dueLocalTime = state.dueLocalTime,
                notBefore = state.notBefore,
                recFreq = if (state.type == TaskType.WINDOW || state.type == TaskType.RECURRING) state.recFreq else existing.recFreq,
                recInterval = state.recInterval.toIntOrNull() ?: 1,
                recAnchor = if (state.type == TaskType.RECURRING) state.recAnchor else existing.recAnchor,
                recDaysOfWeek = state.recDaysOfWeek.toCsv(),
                recDayOfMonth = state.recDayOfMonth.toIntOrNull(),
                recTimesOfDay = state.recTimesOfDay.map(LocalTime::parse).toCsv(),
                recEndDate = state.recEndDate,
                nagIntervalHours = state.nagIntervalHours.toDoubleOrNull(),
                stockQty = state.stockQty.toDoubleOrNull(),
                dosePerIntake = state.dosePerIntake.toDoubleOrNull(),
                restockLeadDays = state.restockLeadDays.toIntOrNull(),
                meterName = state.meterName.ifBlank { null },
                meterInterval = state.meterInterval.toDoubleOrNull(),
                lastDoneMeter = state.lastDoneMeter.toDoubleOrNull(),
                windowHint = state.windowHint.ifBlank { null },
                updatedAt = now,
                dirty = 1
            )
            val stockChanged = updated.stockQty != existing.stockQty
            if (stockChanged) {
                updated = updated.copy(stockRecordedAt = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().toString())
            }
            val nowZdt = Instant.ofEpochMilli(now).atZone(zone)
            val lastDoneAt = completionRepository.getLastByAction(state.taskId, CompletionAction.DONE)?.completedAt
                ?.let { Instant.ofEpochMilli(it).atZone(zone) }
            updated = updated.copy(nextFireAt = NextFireAtCalculator.compute(updated, zone, nowZdt, lastDoneAt))

            taskRepository.update(updated, now)

            val tagIds = state.tags.map { tagRepository.getOrCreate(it).id }.toSet()
            tagRepository.setTagsForTask(state.taskId, tagIds)

            if (state.type in REMINDER_RULE_TYPES) {
                reminderRuleRepository.setForTask(state.taskId, state.reminderOffsets)
            }

            _uiState.value = state.copy(justSaved = true)
        }
    }

    fun delete() {
        val taskId = _uiState.value.taskId
        if (taskId.isEmpty()) return
        viewModelScope.launch {
            taskRepository.softDelete(taskId, nowProvider())
            _uiState.value = _uiState.value.copy(justDeleted = true)
        }
    }

    private companion object {
        val REMINDER_RULE_TYPES = setOf(TaskType.EVENT, TaskType.ANNIVERSARY, TaskType.DEADLINE)
    }
}
