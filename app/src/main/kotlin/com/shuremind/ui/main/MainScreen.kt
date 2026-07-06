package com.shuremind.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.Alignment
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.shuremind.R
import com.shuremind.data.entity.TaskEntity
import com.shuremind.ui.common.HelpDotWithDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onOpenSettings: () -> Unit,
    onOpenTask: (String) -> Unit,
    onOpenMeterReadings: () -> Unit,
    onOpenWeeklyReview: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val capture by viewModel.capture.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onOpenMeterReadings) {
                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = stringResource(R.string.action_meter_readings))
                    }
                    IconButton(onClick = onOpenWeeklyReview) {
                        Icon(Icons.Filled.DateRange, contentDescription = stringResource(R.string.action_weekly_review))
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.action_settings))
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            Column {
                CaptureBar(
                    state = capture,
                    onTitleChange = viewModel::setCaptureTitle,
                    onExpandToggle = { viewModel.setCaptureExpanded(!capture.expanded) },
                    onTypeChange = viewModel::setCaptureType,
                    onImpactChange = viewModel::setCaptureImpact,
                    onUrgencyChange = viewModel::setCaptureUrgency,
                    onTagInputChange = viewModel::setCaptureTagInput,
                    onAddTag = viewModel::addCaptureTag,
                    onRemoveTag = viewModel::removeCaptureTag,
                    onDueDateChange = viewModel::setCaptureDueDate,
                    onDueTimeChange = viewModel::setCaptureDueTime,
                    onCostChange = viewModel::setCaptureCost,
                    onSave = viewModel::saveCapture
                )

                TagFilterRow(
                    allTags = uiState.allTags,
                    selectedTagId = uiState.selectedTagId,
                    onSelectTag = viewModel::selectTag
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Text(stringResource(R.string.list_priority_legend_label), style = MaterialTheme.typography.bodySmall)
                    HelpDotWithDialog(R.string.help_priority_chip)
                }

                TaskSectionsList(
                    uiState = uiState,
                    onOpenTask = onOpenTask,
                    onDone = { viewModel.onDone(it) },
                    onSkip = { viewModel.onSkip(it) },
                    onSnooze = { task, minutes -> viewModel.onSnooze(task, minutes) }
                )
            }
        }
    }
}

@Composable
private fun TagFilterRow(
    allTags: List<com.shuremind.data.entity.TagEntity>,
    selectedTagId: String?,
    onSelectTag: (String?) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp)
    ) {
        item {
            FilterChip(
                selected = selectedTagId == null,
                onClick = { onSelectTag(null) },
                label = { Text(stringResource(R.string.tag_filter_all)) }
            )
        }
        items(allTags, key = { it.id }) { tag ->
            FilterChip(
                selected = selectedTagId == tag.id,
                onClick = { onSelectTag(if (selectedTagId == tag.id) null else tag.id) },
                label = { Text("#${tag.name}") }
            )
        }
    }
}

@Composable
private fun TaskSectionsList(
    uiState: MainUiState,
    onOpenTask: (String) -> Unit,
    onDone: (TaskEntity) -> Unit,
    onSkip: (TaskEntity) -> Unit,
    onSnooze: (TaskEntity, Long) -> Unit
) {
    val sections = listOf(
        R.string.section_overdue to uiState.overdue,
        R.string.section_today to uiState.today,
        R.string.section_upcoming to uiState.upcoming,
        R.string.section_someday to uiState.someday
    ).filter { it.second.isNotEmpty() }

    if (sections.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.list_empty))
        }
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        sections.forEach { (headerRes, items) ->
            item {
                Text(
                    text = stringResource(headerRes),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            items(items, key = { it.task.id }) { item ->
                TaskItemRow(
                    item = item,
                    snoozePresetsMinutes = uiState.snoozePresetsMinutes,
                    onClick = { onOpenTask(item.task.id) },
                    onDone = { onDone(item.task) },
                    onSkip = { onSkip(item.task) },
                    onSnooze = { minutes -> onSnooze(item.task, minutes) }
                )
            }
        }
    }
}
