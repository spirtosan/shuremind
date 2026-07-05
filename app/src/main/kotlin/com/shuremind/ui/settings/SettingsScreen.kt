package com.shuremind.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.shuremind.R
import com.shuremind.data.repo.AppLanguage
import com.shuremind.engine.TaskType
import com.shuremind.ui.common.AppTimePickerDialog
import com.shuremind.ui.common.currentLocale
import com.shuremind.ui.common.formatLocalTime
import com.shuremind.ui.common.formatSnoozeDuration
import com.shuremind.ui.common.labelRes

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val locale = currentLocale()
    val context = LocalContext.current

    var editingTime by remember { mutableStateOf<TimeField?>(null) }
    var snoozeInput by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.nav_back))
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            SettingsSectionTitle(R.string.settings_section_language)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val languages = listOf(
                    AppLanguage.SYSTEM to R.string.lang_system,
                    AppLanguage.ENGLISH to R.string.lang_en,
                    AppLanguage.BULGARIAN to R.string.lang_bg,
                    AppLanguage.RUSSIAN to R.string.lang_ru
                )
                languages.forEach { (language, labelRes) ->
                    FilterChip(
                        selected = state.language == language,
                        onClick = { viewModel.setLanguage(language) },
                        label = { Text(stringResource(labelRes)) }
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            SettingsSectionTitle(R.string.settings_section_quiet_hours)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { editingTime = TimeField.QUIET_START }) {
                    Text(stringResource(R.string.settings_quiet_hours_start_value, formatLocalTime(state.quietHoursStart, locale)))
                }
                TextButton(onClick = { editingTime = TimeField.QUIET_END }) {
                    Text(stringResource(R.string.settings_quiet_hours_end_value, formatLocalTime(state.quietHoursEnd, locale)))
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            SettingsSectionTitle(R.string.settings_section_defaults)
            TextButton(onClick = { editingTime = TimeField.DEFAULT_ALL_DAY }) {
                Text(stringResource(R.string.settings_default_all_day_time_value, formatLocalTime(state.defaultAllDayTime, locale)))
            }
            OutlinedTextField(
                value = state.currency,
                onValueChange = viewModel::setCurrency,
                label = { Text(stringResource(R.string.settings_currency)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            SettingsSectionTitle(R.string.settings_section_snooze)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.snoozePresetsMinutes.forEach { minutes ->
                    InputChip(
                        selected = false,
                        onClick = {
                            viewModel.setSnoozePresetsMinutes(state.snoozePresetsMinutes - minutes)
                        },
                        label = { Text(formatSnoozeDuration(context, minutes)) },
                        trailingIcon = {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.capture_remove_tag), modifier = Modifier.size(16.dp))
                        }
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                OutlinedTextField(
                    value = snoozeInput,
                    onValueChange = { snoozeInput = it },
                    placeholder = { Text(stringResource(R.string.settings_add_snooze_preset)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = {
                    val minutes = snoozeInput.toLongOrNull()
                    if (minutes != null && minutes > 0) {
                        viewModel.setSnoozePresetsMinutes((state.snoozePresetsMinutes + minutes).distinct().sorted())
                        snoozeInput = ""
                    }
                }) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.settings_add_snooze_preset))
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            SettingsSectionTitle(R.string.settings_section_reminder_offsets)
            listOf(TaskType.EVENT, TaskType.ANNIVERSARY, TaskType.DEADLINE).forEach { type ->
                ReminderOffsetsForType(
                    type = type,
                    offsets = state.defaultReminderOffsets[type].orEmpty(),
                    onChange = { viewModel.setDefaultReminderOffsets(type, it) }
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            SettingsSectionTitle(R.string.settings_section_backup)
            DisabledToggleRow(R.string.settings_auto_backup, R.string.settings_auto_backup_subtitle)
            DisabledToggleRow(R.string.settings_exact_alarms, R.string.settings_exact_alarms_subtitle)
        }
    }

    editingTime?.let { field ->
        val initial = when (field) {
            TimeField.QUIET_START -> state.quietHoursStart
            TimeField.QUIET_END -> state.quietHoursEnd
            TimeField.DEFAULT_ALL_DAY -> state.defaultAllDayTime
        }
        AppTimePickerDialog(
            initial = initial,
            onDismiss = { editingTime = null },
            onConfirm = { time ->
                when (field) {
                    TimeField.QUIET_START -> viewModel.setQuietHours(time, state.quietHoursEnd)
                    TimeField.QUIET_END -> viewModel.setQuietHours(state.quietHoursStart, time)
                    TimeField.DEFAULT_ALL_DAY -> viewModel.setDefaultAllDayTime(time)
                }
            }
        )
    }
}

private enum class TimeField { QUIET_START, QUIET_END, DEFAULT_ALL_DAY }

@Composable
private fun SettingsSectionTitle(res: Int) {
    Text(text = stringResource(res), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
}

@Composable
private fun DisabledToggleRow(titleRes: Int, subtitleRes: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(titleRes))
            Text(stringResource(subtitleRes), style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = false, onCheckedChange = null, enabled = false)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReminderOffsetsForType(
    type: TaskType,
    offsets: List<String>,
    onChange: (List<String>) -> Unit
) {
    var input by remember { mutableStateOf("") }
    Column(modifier = Modifier.padding(bottom = 12.dp)) {
        Text(stringResource(type.labelRes()), style = MaterialTheme.typography.bodyLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
            offsets.forEach { offset ->
                InputChip(
                    selected = false,
                    onClick = { onChange(offsets - offset) },
                    label = { Text(offset) },
                    trailingIcon = {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.capture_remove_tag), modifier = Modifier.size(16.dp))
                    }
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text(stringResource(R.string.reminder_offset_hint)) },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            IconButton(onClick = {
                if (input.isNotBlank()) {
                    onChange((offsets + input.trim()).distinct())
                    input = ""
                }
            }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.reminder_offset_add))
            }
        }
    }
}
