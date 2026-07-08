package com.shuremind.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imeNestedScroll
import androidx.compose.foundation.layout.imePadding
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
        // M6 part 1.5: the capture panel, tag filter and legend are items in the same LazyColumn as
        // the task list (rather than siblings above a separate LazyColumn) so the whole screen
        // scrolls as one unit — otherwise the expanded capture panel had no way to scroll into view
        // past the IME, since it lived outside any scrollable container. imePadding() reserves room
        // for the keyboard at the bottom of that shared scroll.
        MainScreenList(
            uiState = uiState,
            capture = capture,
            viewModel = viewModel,
            onOpenTask = onOpenTask,
            modifier = Modifier
                .padding(innerPadding)
                .imePadding()
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MainScreenList(
    uiState: MainUiState,
    capture: QuickCaptureState,
    viewModel: MainViewModel,
    onOpenTask: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val sections = listOf(
        R.string.section_overdue to uiState.overdue,
        R.string.section_today to uiState.today,
        R.string.section_upcoming to uiState.upcoming,
        R.string.section_someday to uiState.someday
    ).filter { it.second.isNotEmpty() }

    LazyColumn(modifier = modifier.fillMaxSize().imeNestedScroll()) {
        item {
            CaptureBar(
                state = capture,
                allTags = uiState.allTags.map { it.name },
                onTitleChange = viewModel::setCaptureTitle,
                onNotesChange = viewModel::setCaptureNotes,
                onExpandToggle = { viewModel.setCaptureExpanded(!capture.expanded) },
                onTypeChange = viewModel::setCaptureType,
                onImpactChange = viewModel::setCaptureImpact,
                onUrgencyChange = viewModel::setCaptureUrgency,
                onTagInputChange = viewModel::setCaptureTagInput,
                onAddTag = viewModel::addCaptureTag,
                onRemoveTag = viewModel::removeCaptureTag,
                onToggleTag = viewModel::toggleCaptureTag,
                onDueDateChange = viewModel::setCaptureDueDate,
                onDueTimeChange = viewModel::setCaptureDueTime,
                onCostChange = viewModel::setCaptureCost,
                onAlarmModeChange = viewModel::setCaptureAlarmMode,
                onSave = viewModel::saveCapture
            )
        }
        item {
            TagFilterRow(
                allTags = uiState.allTags,
                selectedTagId = uiState.selectedTagId,
                onSelectTag = viewModel::selectTag
            )
        }
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Text(stringResource(R.string.list_priority_legend_label), style = MaterialTheme.typography.bodySmall)
                HelpDotWithDialog(R.string.help_priority_chip)
            }
        }
        if (sections.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(top = 64.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.list_empty))
                }
            }
        } else {
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
                        onDone = { viewModel.onDone(item.task) },
                        onSkip = { viewModel.onSkip(item.task) },
                        onSnooze = { minutes -> viewModel.onSnooze(item.task, minutes) }
                    )
                }
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
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
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
