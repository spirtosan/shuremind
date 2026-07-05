package com.shuremind.ui.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shuremind.data.entity.TaskEntity
import com.shuremind.data.repo.CompletionRepository
import com.shuremind.data.repo.SettingsRepository
import com.shuremind.data.repo.TaskRepository
import com.shuremind.engine.CompletionAction
import com.shuremind.engine.TaskStatus
import com.shuremind.engine.TaskType
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZoneId

/** Backs the Weekly review screen (D-28): SOMEDAY / Stale / Windows sections + Done/Snooze/Archive actions. */
class WeeklyReviewViewModel(
    private val taskRepository: TaskRepository,
    private val completionRepository: CompletionRepository,
    private val settingsRepository: SettingsRepository,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val nowProvider: () -> Long = System::currentTimeMillis
) : ViewModel() {

    val uiState: StateFlow<WeeklyReviewUiState> = combine(
        taskRepository.observeActive(),
        settingsRepository.settings
    ) { tasks, settings ->
        buildUiState(tasks.filter { it.status == TaskStatus.ACTIVE }, settings.snoozePresets.map { it.toMinutes() })
    }.stateIn(viewModelScope, SharingStarted.Eagerly, WeeklyReviewUiState())

    private fun buildUiState(tasks: List<TaskEntity>, snoozePresetsMinutes: List<Long>): WeeklyReviewUiState {
        val now = nowProvider()
        val staleThreshold = now - STALE_DAYS * MILLIS_PER_DAY
        val someday = tasks.filter { it.type == TaskType.SOMEDAY }
        val stale = tasks.filter { it.type != TaskType.SOMEDAY && it.nextFireAt != null && it.nextFireAt <= staleThreshold }
        val windows = tasks.filter { it.type == TaskType.WINDOW }
        return WeeklyReviewUiState(someday = someday, stale = stale, windows = windows, snoozePresetsMinutes = snoozePresetsMinutes)
    }

    fun onDone(task: TaskEntity) = viewModelScope.launch {
        completionRepository.completeTask(task, CompletionAction.DONE, nowProvider(), zone)
    }

    fun onSnooze(task: TaskEntity, presetMinutes: Long) = viewModelScope.launch {
        val now = nowProvider()
        taskRepository.snooze(task, until = now + presetMinutes * 60_000L, now = now)
    }

    /** SOMEDAY-only quick action (spec: Archive only offered for SOMEDAY rows). */
    fun onArchive(task: TaskEntity) = viewModelScope.launch {
        val now = nowProvider()
        taskRepository.update(task.copy(status = TaskStatus.ARCHIVED, updatedAt = now, dirty = 1), now)
    }

    private companion object {
        const val STALE_DAYS = 7L
        const val MILLIS_PER_DAY = 86_400_000L
    }
}
