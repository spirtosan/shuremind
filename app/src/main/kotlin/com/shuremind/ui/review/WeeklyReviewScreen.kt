package com.shuremind.ui.review

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.shuremind.R
import com.shuremind.data.entity.TaskEntity
import com.shuremind.ui.common.HelpDotWithDialog
import com.shuremind.ui.common.formatSnoozeDuration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeeklyReviewScreen(
    viewModel: WeeklyReviewViewModel,
    onBack: () -> Unit,
    onOpenTask: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.weekly_review_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.nav_back))
                    }
                },
                actions = {
                    HelpDotWithDialog(R.string.help_weekly_review)
                }
            )
        }
    ) { innerPadding ->
        val sections = listOf(
            R.string.weekly_review_section_someday to uiState.someday,
            R.string.weekly_review_section_stale to uiState.stale,
            R.string.weekly_review_section_windows to uiState.windows
        ).filter { it.second.isNotEmpty() }

        if (sections.isEmpty()) {
            Box(modifier = Modifier.padding(innerPadding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.weekly_review_empty))
            }
            return@Scaffold
        }

        LazyColumn(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            sections.forEach { (headerRes, tasks) ->
                item {
                    Text(
                        text = stringResource(headerRes),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                items(tasks, key = { it.id }) { task ->
                    WeeklyReviewRow(
                        task = task,
                        snoozePresetsMinutes = uiState.snoozePresetsMinutes,
                        onClick = { onOpenTask(task.id) },
                        onDone = { viewModel.onDone(task) },
                        onSnooze = { minutes -> viewModel.onSnooze(task, minutes) },
                        onArchive = { viewModel.onArchive(task) }
                    )
                }
            }
        }
    }
}

@Composable
private fun WeeklyReviewRow(
    task: TaskEntity,
    snoozePresetsMinutes: List<Long>,
    onClick: () -> Unit,
    onDone: () -> Unit,
    onSnooze: (Long) -> Unit,
    onArchive: () -> Unit
) {
    val context = LocalContext.current
    var showSnoozeMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(task.title, style = MaterialTheme.typography.bodyLarge)
        }
        IconButton(onClick = onDone) {
            Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.action_done))
        }
        IconButton(onClick = { showSnoozeMenu = true }) {
            Icon(Icons.Filled.Notifications, contentDescription = stringResource(R.string.action_snooze))
            DropdownMenu(expanded = showSnoozeMenu, onDismissRequest = { showSnoozeMenu = false }) {
                snoozePresetsMinutes.forEach { minutes ->
                    DropdownMenuItem(
                        text = { Text(formatSnoozeDuration(context, minutes)) },
                        onClick = {
                            showSnoozeMenu = false
                            onSnooze(minutes)
                        }
                    )
                }
            }
        }
        if (task.type == com.shuremind.engine.TaskType.SOMEDAY) {
            IconButton(onClick = onArchive) {
                Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = stringResource(R.string.weekly_review_archive))
            }
        }
    }
}
