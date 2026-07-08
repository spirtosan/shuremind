package com.shuremind.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shuremind.data.entity.TaskEntity
import com.shuremind.data.repo.CompletionRepository
import com.shuremind.data.repo.MeterRepository
import com.shuremind.data.repo.NextFireAtCalculator
import com.shuremind.data.repo.SettingsRepository
import com.shuremind.data.repo.TagRepository
import com.shuremind.data.repo.TaskRepository
import com.shuremind.engine.CompletionAction
import com.shuremind.engine.MeterEngine
import com.shuremind.engine.PriorityEngine
import com.shuremind.engine.TaskStatus
import com.shuremind.engine.TaskType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID

class MainViewModel(
    private val taskRepository: TaskRepository,
    private val completionRepository: CompletionRepository,
    private val tagRepository: TagRepository,
    private val settingsRepository: SettingsRepository,
    private val meterRepository: MeterRepository,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val nowProvider: () -> Long = System::currentTimeMillis
) : ViewModel() {

    private val selectedTagId = MutableStateFlow<String?>(null)

    private data class Sources(
        val tasks: List<TaskEntity>,
        val tagMap: Map<String, List<com.shuremind.data.entity.TagEntity>>,
        val allTags: List<com.shuremind.data.entity.TagEntity>,
        val snoozePresetsMinutes: List<Long>,
        val selectedTag: String?
    )

    val uiState: StateFlow<MainUiState> = combine(
        taskRepository.observeActive(),
        tagRepository.observeTaskTagMap(),
        tagRepository.observeAll(),
        settingsRepository.settings,
        selectedTagId
    ) { tasks, tagMap, allTags, settings, selectedTag ->
        Sources(tasks, tagMap, allTags, settings.snoozePresets.map { it.toMinutes() }, selectedTag)
    }.combine(meterRepository.observeAll()) { sources, meterReadings ->
        val latestMeterValues = meterReadings.groupBy { it.meterName }
            .mapValues { (_, readings) -> readings.maxBy { it.recordedAt }.value }
        buildUiState(sources.tasks, sources.tagMap, sources.allTags, sources.snoozePresetsMinutes, sources.selectedTag, latestMeterValues)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, MainUiState())

    private val _capture = MutableStateFlow(QuickCaptureState())
    val capture: StateFlow<QuickCaptureState> = _capture

    private fun buildUiState(
        tasks: List<TaskEntity>,
        tagMap: Map<String, List<com.shuremind.data.entity.TagEntity>>,
        allTags: List<com.shuremind.data.entity.TagEntity>,
        snoozePresetsMinutes: List<Long>,
        selectedTag: String?,
        latestMeterValues: Map<String, Double>
    ): MainUiState {
        val now = nowProvider()
        val nowZdt = Instant.ofEpochMilli(now).atZone(zone)
        val today = nowZdt.toLocalDate()

        val visible = if (selectedTag == null) tasks else tasks.filter { selectedTag in tagMap[it.id].orEmpty().map { t -> t.id } }

        val overdue = mutableListOf<TaskListItem>()
        val todayList = mutableListOf<TaskListItem>()
        val upcoming = mutableListOf<TaskListItem>()
        val someday = mutableListOf<TaskListItem>()

        for (task in visible) {
            val tags = tagMap[task.id].orEmpty()
            val isSnoozedActive = task.snoozedUntil != null && task.snoozedUntil > now
            val nextFireAt = task.nextFireAt
            // D-27: meter-due (km-since-last-done OR-logic) surfaces in Overdue at read time even
            // when the task's own time-based next_fire_at hasn't arrived yet — no alarm instant is
            // ever created for this (by design, Session 2 note); this is purely a list-placement check.
            val meterDue = isMeterDue(task, latestMeterValues)
            if (task.type == TaskType.SOMEDAY || (nextFireAt == null && !meterDue)) {
                someday += TaskListItem(task, tags, score = null, isSnoozedActive = isSnoozedActive)
                continue
            }
            if (meterDue) {
                val score = PriorityEngine.computeScore(task.impact, task.urgency, daysToDue = -1)
                overdue += TaskListItem(task, tags, score, isSnoozedActive)
                continue
            }
            val dueDate = Instant.ofEpochMilli(nextFireAt!!).atZone(zone).toLocalDate()
            val daysToDue = ChronoUnit.DAYS.between(today, dueDate)
            val score = PriorityEngine.computeScore(task.impact, task.urgency, daysToDue)
            val item = TaskListItem(task, tags, score, isSnoozedActive)
            when {
                nextFireAt <= now -> overdue += item
                dueDate == today -> todayList += item
                else -> upcoming += item
            }
        }

        val byScoreThenDue = compareByDescending<TaskListItem> { it.score ?: -1 }
            .thenBy { it.task.nextFireAt ?: Long.MAX_VALUE }

        return MainUiState(
            overdue = overdue.sortedWith(byScoreThenDue),
            today = todayList.sortedWith(byScoreThenDue),
            upcoming = upcoming.sortedWith(byScoreThenDue),
            someday = someday.sortedBy { it.task.title.lowercase() },
            allTags = allTags,
            selectedTagId = selectedTag,
            snoozePresetsMinutes = snoozePresetsMinutes
        )
    }

    private fun isMeterDue(task: TaskEntity, latestMeterValues: Map<String, Double>): Boolean {
        val meterName = task.meterName ?: return false
        val latest = latestMeterValues[meterName] ?: return false
        val lastDone = task.lastDoneMeter ?: return false
        val interval = task.meterInterval ?: return false
        return MeterEngine.isMeterDue(latest, lastDone, interval)
    }

    fun selectTag(tagId: String?) {
        selectedTagId.value = tagId
    }

    // --- Quick capture (task 3) ---

    fun setCaptureTitle(title: String) = updateCapture { it.copy(title = title) }
    fun setCaptureNotes(notes: String) = updateCapture { it.copy(notes = notes) }
    fun setCaptureExpanded(expanded: Boolean) = updateCapture { it.copy(expanded = expanded) }
    fun setCaptureType(type: TaskType) = updateCapture { it.copy(type = type) }
    fun setCaptureImpact(impact: Int) = updateCapture { it.copy(impact = impact) }
    fun setCaptureUrgency(urgency: Int) = updateCapture { it.copy(urgency = urgency) }
    fun setCaptureTagInput(text: String) = updateCapture { it.copy(tagInput = text) }
    fun setCaptureDueDate(isoDate: String?) = updateCapture { it.copy(dueLocalDate = isoDate) }
    fun setCaptureDueTime(isoTime: String?) = updateCapture { it.copy(dueLocalTime = isoTime) }
    fun setCaptureCost(cost: String) = updateCapture { it.copy(estimatedCost = cost) }
    fun setCaptureAlarmMode(alarmMode: Boolean) = updateCapture { it.copy(alarmMode = alarmMode) }

    fun addCaptureTag() {
        val name = _capture.value.tagInput.trim()
        if (name.isEmpty()) return
        updateCapture { it.copy(tags = (it.tags + name).distinct(), tagInput = "") }
    }

    fun removeCaptureTag(name: String) = updateCapture { it.copy(tags = it.tags - name) }

    /** D-39 tag picker (M6 part 1.5): toggling an existing tag chip on/off the in-progress capture. */
    fun toggleCaptureTag(name: String) = updateCapture { it.copy(tags = if (name in it.tags) it.tags - name else it.tags + name) }

    private fun updateCapture(block: (QuickCaptureState) -> QuickCaptureState) {
        _capture.value = block(_capture.value)
    }

    fun saveCapture() {
        val state = _capture.value
        if (!state.canSave) return
        viewModelScope.launch {
            val now = nowProvider()
            val nowZdt = Instant.ofEpochMilli(now).atZone(zone)
            val id = UUID.randomUUID().toString()
            var entity = TaskEntity(
                id = id,
                title = state.title.trim(),
                notes = state.notes.trim().ifBlank { null },
                type = state.type,
                status = TaskStatus.ACTIVE,
                impact = state.impact,
                urgency = state.urgency,
                estimatedCost = state.estimatedCost.toDoubleOrNull(),
                dueLocalDate = state.dueLocalDate,
                dueLocalTime = state.dueLocalTime,
                notBefore = null,
                recFreq = null,
                recInterval = 1,
                recAnchor = null,
                recDaysOfWeek = null,
                recDayOfMonth = null,
                recTimesOfDay = null,
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
                alarmMode = state.alarmMode,
                snoozedUntil = null,
                nextFireAt = null,
                createdAt = now,
                updatedAt = now,
                deletedAt = null,
                dirty = 1
            )
            entity = entity.copy(nextFireAt = NextFireAtCalculator.compute(entity, zone, nowZdt, lastDoneAt = null))
            taskRepository.upsert(entity, now)
            if (state.tags.isNotEmpty()) {
                val tagIds = state.tags.map { tagRepository.getOrCreate(it).id }.toSet()
                tagRepository.setTagsForTask(id, tagIds)
            }
            _capture.value = QuickCaptureState()
        }
    }

    // --- List item actions (task 4) ---

    fun onDone(task: TaskEntity) = viewModelScope.launch {
        completionRepository.completeTask(task, CompletionAction.DONE, nowProvider(), zone)
    }

    fun onSkip(task: TaskEntity) = viewModelScope.launch {
        completionRepository.completeTask(task, CompletionAction.SKIPPED, nowProvider(), zone)
    }

    fun onSnooze(task: TaskEntity, presetMinutes: Long) = viewModelScope.launch {
        val now = nowProvider()
        taskRepository.snooze(task, until = now + presetMinutes * 60_000L, now = now)
    }
}
