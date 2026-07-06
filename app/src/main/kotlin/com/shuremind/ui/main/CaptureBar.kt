package com.shuremind.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.shuremind.R
import com.shuremind.engine.TaskType
import com.shuremind.ui.common.AppDatePickerDialog
import com.shuremind.ui.common.AppTimePickerDialog
import com.shuremind.ui.common.HelpDotWithDialog
import com.shuremind.ui.common.LeveledStepperRow
import com.shuremind.ui.common.TypeHelpDot
import com.shuremind.ui.common.currentLocale
import com.shuremind.ui.common.formatLocalDate
import com.shuremind.ui.common.formatLocalTime
import com.shuremind.ui.common.impactLevelLabelRes
import com.shuremind.ui.common.labelRes
import com.shuremind.ui.common.urgencyLevelLabelRes
import java.time.LocalDate
import java.time.LocalTime

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CaptureBar(
    state: QuickCaptureState,
    onTitleChange: (String) -> Unit,
    onExpandToggle: () -> Unit,
    onTypeChange: (TaskType) -> Unit,
    onImpactChange: (Int) -> Unit,
    onUrgencyChange: (Int) -> Unit,
    onTagInputChange: (String) -> Unit,
    onAddTag: () -> Unit,
    onRemoveTag: (String) -> Unit,
    onDueDateChange: (String?) -> Unit,
    onDueTimeChange: (String?) -> Unit,
    onCostChange: (String) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    val locale = currentLocale()
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.title,
                    onValueChange = onTitleChange,
                    placeholder = { Text(stringResource(R.string.capture_hint)) },
                    modifier = Modifier
                        .weight(1f),
                    singleLine = true
                )
                IconButton(onClick = onExpandToggle) {
                    Icon(
                        imageVector = if (state.expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                        contentDescription = stringResource(if (state.expanded) R.string.capture_collapse else R.string.capture_expand)
                    )
                }
                TextButton(onClick = onSave, enabled = state.canSave) {
                    Text(stringResource(R.string.capture_save))
                }
            }

            if (state.expanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.capture_type_label))
                        TypeHelpDot()
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TaskType.entries.forEach { type ->
                            FilterChip(
                                selected = state.type == type,
                                onClick = { onTypeChange(type) },
                                label = { Text(stringResource(type.labelRes())) }
                            )
                        }
                    }

                    LeveledStepperRow(
                        R.string.capture_impact_label, state.impact, onImpactChange, ::impactLevelLabelRes,
                        modifier = Modifier.padding(top = 8.dp),
                        headerTrailing = { HelpDotWithDialog(R.string.help_impact) }
                    )
                    LeveledStepperRow(
                        R.string.capture_urgency_label, state.urgency, onUrgencyChange, ::urgencyLevelLabelRes,
                        modifier = Modifier.padding(top = 8.dp),
                        headerTrailing = { HelpDotWithDialog(R.string.help_urgency) }
                    )

                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                        Text(stringResource(R.string.capture_tags_label))
                        HelpDotWithDialog(R.string.help_tags)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = state.tagInput,
                            onValueChange = onTagInputChange,
                            placeholder = { Text(stringResource(R.string.capture_tag_input_hint)) },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        IconButton(onClick = onAddTag) {
                            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.capture_add_tag))
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.tags.forEach { tag ->
                            InputChip(
                                selected = false,
                                onClick = { onRemoveTag(tag) },
                                label = { Text(tag) },
                                trailingIcon = {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = stringResource(R.string.capture_remove_tag),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        TextButton(onClick = { showDatePicker = true }) {
                            Text(state.dueLocalDate?.let { formatLocalDate(LocalDate.parse(it), locale) } ?: stringResource(R.string.capture_pick_date))
                        }
                        if (state.dueLocalDate != null) {
                            IconButton(onClick = { onDueDateChange(null); onDueTimeChange(null) }) {
                                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.capture_clear_date))
                            }
                        }
                        TextButton(onClick = { showTimePicker = true }, enabled = state.dueLocalDate != null) {
                            Text(state.dueLocalTime?.let { formatLocalTime(LocalTime.parse(it), locale) } ?: stringResource(R.string.capture_pick_time))
                        }
                        if (state.dueLocalTime != null) {
                            IconButton(onClick = { onDueTimeChange(null) }) {
                                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.capture_clear_time))
                            }
                        }
                    }

                    OutlinedTextField(
                        value = state.estimatedCost,
                        onValueChange = onCostChange,
                        label = { Text(stringResource(R.string.capture_cost_label)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }

    if (showDatePicker) {
        AppDatePickerDialog(
            initial = state.dueLocalDate?.let(LocalDate::parse),
            onDismiss = { showDatePicker = false },
            onConfirm = { onDueDateChange(it.toString()) }
        )
    }
    if (showTimePicker) {
        AppTimePickerDialog(
            initial = state.dueLocalTime?.let(LocalTime::parse),
            onDismiss = { showTimePicker = false },
            onConfirm = { onDueTimeChange(it.toString()) }
        )
    }
}
