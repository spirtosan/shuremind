package com.shuremind.ui.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.shuremind.R
import com.shuremind.ui.common.currentLocale
import com.shuremind.ui.common.formatEpochMillis
import com.shuremind.ui.common.formatSnoozeDuration
import com.shuremind.ui.common.labelRes
import java.time.ZoneId

@Composable
fun TaskItemRow(
    item: TaskListItem,
    snoozePresetsMinutes: List<Long>,
    onClick: () -> Unit,
    onDone: () -> Unit,
    onSkip: () -> Unit,
    onSnooze: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val locale = currentLocale()
    val zone = remember { ZoneId.systemDefault() }
    var showSnoozeMenu by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .alpha(if (item.isSnoozedActive) 0.5f else 1f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(item.task.title, style = MaterialTheme.typography.bodyLarge)
            val subtitle = buildString {
                append(stringResource(item.task.type.labelRes()))
                item.task.nextFireAt?.let {
                    append(" · ")
                    append(formatEpochMillis(it, zone, locale))
                }
                if (item.isSnoozedActive) {
                    item.task.snoozedUntil?.let {
                        append(" · ")
                        append(stringResource(R.string.snoozed_until_label, formatEpochMillis(it, zone, locale)))
                    }
                }
            }
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
            if (item.tags.isNotEmpty()) {
                Text(item.tags.joinToString(" ") { "#${it.name}" }, style = MaterialTheme.typography.bodySmall)
            }
        }
        item.score?.let { score ->
            Text(score.toString(), style = MaterialTheme.typography.titleMedium)
        }
        IconButton(onClick = onDone) {
            Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.action_done))
        }
        IconButton(onClick = onSkip) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = stringResource(R.string.action_skip))
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
    }
}
