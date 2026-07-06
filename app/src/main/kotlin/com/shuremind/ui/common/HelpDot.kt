package com.shuremind.ui.common

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.shuremind.R
import com.shuremind.engine.TaskType

/**
 * D-40: circled "?" (custom-drawn, no icon dependency per D-29), 48dp touch target around a 20dp
 * visual per accessibility norms. Callers own what happens on tap (usually [HelpDotWithDialog]).
 */
@Composable
fun HelpDot(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick, role = Role.Button),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .border(1.dp, MaterialTheme.colorScheme.onSurfaceVariant, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "?",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** D-40: the common case — a HelpDot that opens a single short help string in an AlertDialog with a Close button. */
@Composable
fun HelpDotWithDialog(textRes: Int, modifier: Modifier = Modifier) {
    var show by remember { mutableStateOf(false) }
    HelpDot(onClick = { show = true }, modifier = modifier)
    if (show) {
        AlertDialog(
            onDismissRequest = { show = false },
            text = { Text(stringResource(textRes)) },
            confirmButton = {
                TextButton(onClick = { show = false }) { Text(stringResource(R.string.help_close)) }
            }
        )
    }
}

/** D-40: the type picker's HelpDot opens one dialog listing all 8 types with a one-sentence explanation each. */
@Composable
fun TypeHelpDot(modifier: Modifier = Modifier) {
    var show by remember { mutableStateOf(false) }
    HelpDot(onClick = { show = true }, modifier = modifier)
    if (show) {
        AlertDialog(
            onDismissRequest = { show = false },
            title = { Text(stringResource(R.string.help_type_picker_title)) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    TaskType.entries.forEach { type ->
                        Text(stringResource(type.labelRes()), style = MaterialTheme.typography.titleSmall)
                        Text(
                            stringResource(type.helpRes()),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { show = false }) { Text(stringResource(R.string.help_close)) }
            }
        )
    }
}
