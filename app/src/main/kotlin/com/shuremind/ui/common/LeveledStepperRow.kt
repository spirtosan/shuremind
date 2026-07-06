package com.shuremind.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/**
 * D-39: the 0-3 impact/urgency selector, shared by the capture bar and task detail screen. Each
 * step shows both the number and its localized word label (e.g. "2 Important") so the meaning of
 * the scale doesn't have to be memorized.
 */
@Composable
fun LeveledStepperRow(
    headerRes: Int,
    value: Int,
    onChange: (Int) -> Unit,
    levelLabelRes: (Int) -> Int,
    modifier: Modifier = Modifier,
    headerTrailing: @Composable () -> Unit = {}
) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(headerRes))
            headerTrailing()
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
            (0..3).forEach { level ->
                FilterChip(
                    selected = value == level,
                    onClick = { onChange(level) },
                    label = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(level.toString(), style = MaterialTheme.typography.bodyMedium)
                            Text(stringResource(levelLabelRes(level)), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                )
            }
        }
    }
}
