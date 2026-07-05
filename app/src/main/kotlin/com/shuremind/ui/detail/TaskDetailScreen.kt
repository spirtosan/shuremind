package com.shuremind.ui.detail

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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import com.shuremind.engine.RecurrenceAnchor
import com.shuremind.engine.RecurrenceFrequency
import com.shuremind.engine.TaskType
import com.shuremind.ui.common.AppDatePickerDialog
import com.shuremind.ui.common.AppTimePickerDialog
import com.shuremind.ui.common.currentLocale
import com.shuremind.ui.common.formatLocalDate
import com.shuremind.ui.common.formatLocalTime
import com.shuremind.ui.common.labelRes
import com.shuremind.ui.common.shortLabelRes
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TaskDetailScreen(
    taskId: String,
    viewModel: TaskDetailViewModel,
    onBack: () -> Unit,
    autoOpenRestock: Boolean = false
) {
    val state by viewModel.uiState.collectAsState()
    val locale = currentLocale()

    LaunchedEffect(taskId, autoOpenRestock) { viewModel.load(taskId, autoOpenRestock) }
    LaunchedEffect(state.justSaved, state.justDeleted) {
        if (state.justSaved || state.justDeleted) onBack()
    }

    var showDeleteConfirm by remember { mutableStateOf(false) }
    var datePickerTarget by remember { mutableStateOf<DetailDateField?>(null) }
    var timePickerTarget by remember { mutableStateOf<DetailTimeField?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.nav_back))
                    }
                },
                actions = {
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.detail_delete))
                    }
                    TextButton(onClick = viewModel::save, enabled = state.canSave) {
                        Text(stringResource(R.string.detail_save))
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
            SectionTitle(R.string.detail_section_common)
            OutlinedTextField(
                value = state.title,
                onValueChange = viewModel::setTitle,
                label = { Text(stringResource(R.string.field_title)) },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = state.notes,
                onValueChange = viewModel::setNotes,
                label = { Text(stringResource(R.string.field_notes)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            )
            FlowRow(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TaskType.entries.forEach { type ->
                    FilterChip(
                        selected = state.type == type,
                        onClick = { viewModel.setType(type) },
                        label = { Text(stringResource(type.labelRes())) }
                    )
                }
            }
            StepperRow(R.string.field_impact, state.impact, viewModel::setImpact)
            StepperRow(R.string.field_urgency, state.urgency, viewModel::setUrgency)
            OutlinedTextField(
                value = state.estimatedCost,
                onValueChange = viewModel::setEstimatedCost,
                label = { Text(stringResource(R.string.field_cost)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            )

            if (state.type in setOf(TaskType.EVENT, TaskType.ANNIVERSARY, TaskType.DEADLINE)) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                SectionTitle(R.string.detail_section_schedule)
                DateTimeRow(
                    dateLabelRes = R.string.field_due_date,
                    date = state.dueLocalDate?.let(LocalDate::parse),
                    time = state.dueLocalTime?.let(LocalTime::parse),
                    locale = locale,
                    onPickDate = { datePickerTarget = DetailDateField.DUE },
                    onPickTime = { timePickerTarget = DetailTimeField.DUE },
                    onClearDate = { viewModel.setDueLocalDate(null); viewModel.setDueLocalTime(null) },
                    onClearTime = { viewModel.setDueLocalTime(null) }
                )
                ReminderOffsetsEditor(
                    offsets = state.reminderOffsets,
                    onAdd = viewModel::addReminderOffset,
                    onRemove = viewModel::removeReminderOffset
                )
            }

            if (state.type in setOf(TaskType.WINDOW, TaskType.NAG, TaskType.RECURRING)) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                SectionTitle(R.string.detail_section_recurrence)

                if (state.type == TaskType.NAG) {
                    OutlinedTextField(
                        value = state.nagIntervalHours,
                        onValueChange = viewModel::setNagIntervalHours,
                        label = { Text(stringResource(R.string.field_nag_interval_hours)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (state.type == TaskType.RECURRING) {
                    Text(stringResource(R.string.field_rec_anchor), modifier = Modifier.padding(top = 8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RecurrenceAnchor.entries.forEach { anchor ->
                            FilterChip(
                                selected = state.recAnchor == anchor,
                                onClick = { viewModel.setRecAnchor(anchor) },
                                label = { Text(stringResource(anchor.labelRes())) }
                            )
                        }
                    }
                }

                if (state.type == TaskType.WINDOW || state.type == TaskType.RECURRING) {
                    Text(stringResource(R.string.field_rec_freq), modifier = Modifier.padding(top = 8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RecurrenceFrequency.entries.forEach { freq ->
                            FilterChip(
                                selected = state.recFreq == freq,
                                onClick = { viewModel.setRecFreq(freq) },
                                label = { Text(stringResource(freq.labelRes())) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = state.recInterval,
                        onValueChange = viewModel::setRecInterval,
                        label = { Text(stringResource(R.string.field_rec_interval)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    )
                    if (state.recFreq == RecurrenceFrequency.WEEKLY) {
                        Text(stringResource(R.string.field_rec_days_of_week), modifier = Modifier.padding(top = 8.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DayOfWeek.values().forEach { day ->
                                FilterChip(
                                    selected = day in state.recDaysOfWeek,
                                    onClick = { viewModel.toggleRecDayOfWeek(day) },
                                    label = { Text(stringResource(day.shortLabelRes())) }
                                )
                            }
                        }
                    }
                    if (state.recFreq == RecurrenceFrequency.MONTHLY) {
                        OutlinedTextField(
                            value = state.recDayOfMonth,
                            onValueChange = viewModel::setRecDayOfMonth,
                            label = { Text(stringResource(R.string.field_rec_day_of_month)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                        )
                    }
                    TimesOfDayEditor(
                        times = state.recTimesOfDay,
                        onAdd = viewModel::addRecTimeOfDay,
                        onRemove = viewModel::removeRecTimeOfDay
                    )
                    Row(modifier = Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { datePickerTarget = DetailDateField.REC_END }) {
                            Text(
                                state.recEndDate?.let { formatLocalDate(LocalDate.parse(it), locale) }
                                    ?: stringResource(R.string.field_rec_end_date)
                            )
                        }
                        if (state.recEndDate != null) {
                            IconButton(onClick = { viewModel.setRecEndDate(null) }) {
                                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.capture_clear_date))
                            }
                        }
                    }
                }

                if (state.type == TaskType.WINDOW) {
                    OutlinedTextField(
                        value = state.windowHint,
                        onValueChange = viewModel::setWindowHint,
                        label = { Text(stringResource(R.string.field_window_hint)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                    SectionTitle(R.string.detail_section_date_learned)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { datePickerTarget = DetailDateField.DATE_LEARNED }) {
                            Text(
                                state.dateLearnedDate?.let { formatLocalDate(LocalDate.parse(it), locale) }
                                    ?: stringResource(R.string.detail_date_learned_pick)
                            )
                        }
                        TextButton(
                            onClick = { timePickerTarget = DetailTimeField.DATE_LEARNED },
                            enabled = state.dateLearnedDate != null
                        ) {
                            Text(
                                state.dateLearnedTime?.let { formatLocalTime(LocalTime.parse(it), locale) }
                                    ?: stringResource(R.string.field_due_time)
                            )
                        }
                    }
                    TextButton(
                        onClick = viewModel::convertWindowToDeadline,
                        enabled = state.dateLearnedDate != null
                    ) {
                        Text(stringResource(R.string.detail_convert_to_deadline))
                    }
                }

                if (state.type == TaskType.RECURRING) {
                    SectionTitle(R.string.detail_section_meter, topPadding = 16.dp)
                    OutlinedTextField(
                        value = state.meterName,
                        onValueChange = viewModel::setMeterName,
                        label = { Text(stringResource(R.string.field_meter_name)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = state.meterInterval,
                        onValueChange = viewModel::setMeterInterval,
                        label = { Text(stringResource(R.string.field_meter_interval)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    )
                    OutlinedTextField(
                        value = state.lastDoneMeter,
                        onValueChange = viewModel::setLastDoneMeter,
                        label = { Text(stringResource(R.string.field_last_done_meter)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    )
                }

                Row(modifier = Modifier.padding(top = 8.dp)) {
                    TextButton(onClick = { datePickerTarget = DetailDateField.NOT_BEFORE }) {
                        Text(
                            state.notBefore?.let { formatLocalDate(LocalDate.parse(it), locale) }
                                ?: stringResource(R.string.field_not_before)
                        )
                    }
                    if (state.notBefore != null) {
                        IconButton(onClick = { viewModel.setNotBefore(null) }) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.capture_clear_date))
                        }
                    }
                }
            }

            if (state.type == TaskType.CONSUMABLE) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                SectionTitle(R.string.detail_section_consumable)
                OutlinedTextField(
                    value = state.stockQty,
                    onValueChange = viewModel::setStockQty,
                    label = { Text(stringResource(R.string.field_stock_qty)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = state.dosePerIntake,
                    onValueChange = viewModel::setDosePerIntake,
                    label = { Text(stringResource(R.string.field_dose_per_intake)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )
                OutlinedTextField(
                    value = state.restockLeadDays,
                    onValueChange = viewModel::setRestockLeadDays,
                    label = { Text(stringResource(R.string.field_restock_lead_days)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )
                TimesOfDayEditor(
                    times = state.recTimesOfDay,
                    onAdd = viewModel::addRecTimeOfDay,
                    onRemove = viewModel::removeRecTimeOfDay
                )
                state.stockRecordedAt?.let {
                    Text(
                        stringResource(R.string.field_stock_recorded_at_value, formatLocalDate(LocalDate.parse(it), locale)),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                state.remainingStock?.let { remaining ->
                    Text(
                        stringResource(R.string.field_remaining_stock_value, remaining),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                state.runOutDate?.let {
                    Text(
                        stringResource(R.string.field_run_out_date_value, formatLocalDate(LocalDate.parse(it), locale)),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                TextButton(onClick = viewModel::openRestockDialog, modifier = Modifier.padding(top = 8.dp)) {
                    Text(stringResource(R.string.detail_restock_action))
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            SectionTitle(R.string.detail_section_tags)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.tagInput,
                    onValueChange = viewModel::setTagInput,
                    placeholder = { Text(stringResource(R.string.capture_tag_input_hint)) },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                IconButton(onClick = viewModel::addTag) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.capture_add_tag))
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                state.tags.forEach { tag ->
                    InputChip(
                        selected = false,
                        onClick = { viewModel.removeTag(tag) },
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
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.detail_delete_confirm_title)) },
            text = { Text(stringResource(R.string.detail_delete_confirm_message)) },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; viewModel.delete() }) {
                    Text(stringResource(R.string.detail_delete_confirm_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.detail_delete_confirm_cancel))
                }
            }
        )
    }

    datePickerTarget?.let { target ->
        val initial = when (target) {
            DetailDateField.DUE -> state.dueLocalDate?.let(LocalDate::parse)
            DetailDateField.NOT_BEFORE -> state.notBefore?.let(LocalDate::parse)
            DetailDateField.REC_END -> state.recEndDate?.let(LocalDate::parse)
            DetailDateField.DATE_LEARNED -> state.dateLearnedDate?.let(LocalDate::parse)
        }
        AppDatePickerDialog(
            initial = initial,
            onDismiss = { datePickerTarget = null },
            onConfirm = { date ->
                when (target) {
                    DetailDateField.DUE -> viewModel.setDueLocalDate(date.toString())
                    DetailDateField.NOT_BEFORE -> viewModel.setNotBefore(date.toString())
                    DetailDateField.REC_END -> viewModel.setRecEndDate(date.toString())
                    DetailDateField.DATE_LEARNED -> viewModel.setDateLearnedDate(date.toString())
                }
            }
        )
    }
    timePickerTarget?.let { target ->
        val initial = when (target) {
            DetailTimeField.DUE -> state.dueLocalTime?.let(LocalTime::parse)
            DetailTimeField.DATE_LEARNED -> state.dateLearnedTime?.let(LocalTime::parse)
        }
        AppTimePickerDialog(
            initial = initial,
            onDismiss = { timePickerTarget = null },
            onConfirm = { time ->
                when (target) {
                    DetailTimeField.DUE -> viewModel.setDueLocalTime(time.toString())
                    DetailTimeField.DATE_LEARNED -> viewModel.setDateLearnedTime(time.toString())
                }
            }
        )
    }

    if (state.showRestockDialog) {
        RestockDialog(
            boughtQuantity = state.boughtQuantity,
            onBoughtQuantityChange = viewModel::setBoughtQuantity,
            onDismiss = viewModel::dismissRestockDialog,
            onConfirm = viewModel::confirmRestock
        )
    }
}

private enum class DetailDateField { DUE, NOT_BEFORE, REC_END, DATE_LEARNED }
private enum class DetailTimeField { DUE, DATE_LEARNED }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RestockDialog(
    boughtQuantity: String,
    onBoughtQuantityChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.detail_restock_dialog_title)) },
        text = {
            OutlinedTextField(
                value = boughtQuantity,
                onValueChange = onBoughtQuantityChange,
                label = { Text(stringResource(R.string.detail_restock_dialog_hint)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }, enabled = boughtQuantity.toDoubleOrNull() != null) {
                Text(stringResource(R.string.detail_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.detail_delete_confirm_cancel)) }
        }
    )
}

@Composable
private fun SectionTitle(res: Int, topPadding: androidx.compose.ui.unit.Dp = 0.dp) {
    Text(
        text = stringResource(res),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = topPadding, bottom = 8.dp)
    )
}

@Composable
private fun StepperRow(labelRes: Int, value: Int, onChange: (Int) -> Unit) {
    Column(modifier = Modifier.padding(top = 8.dp)) {
        Text(stringResource(labelRes))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (0..3).forEach { level ->
                FilterChip(
                    selected = value == level,
                    onClick = { onChange(level) },
                    label = { Text(level.toString()) }
                )
            }
        }
    }
}

@Composable
private fun DateTimeRow(
    dateLabelRes: Int,
    date: LocalDate?,
    time: LocalTime?,
    locale: java.util.Locale,
    onPickDate: () -> Unit,
    onPickTime: () -> Unit,
    onClearDate: () -> Unit,
    onClearTime: () -> Unit
) {
    Column(modifier = Modifier.padding(top = 8.dp)) {
        Text(stringResource(dateLabelRes))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onPickDate) {
                Text(date?.let { formatLocalDate(it, locale) } ?: stringResource(R.string.capture_pick_date))
            }
            if (date != null) {
                IconButton(onClick = onClearDate) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.capture_clear_date))
                }
            }
            TextButton(onClick = onPickTime, enabled = date != null) {
                Text(time?.let { formatLocalTime(it, locale) } ?: stringResource(R.string.field_due_time))
            }
            if (time != null) {
                IconButton(onClick = onClearTime) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.capture_clear_time))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReminderOffsetsEditor(
    offsets: List<String>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit
) {
    var input by remember { mutableStateOf("") }
    Column(modifier = Modifier.padding(top = 8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text(stringResource(R.string.reminder_offset_hint)) },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            IconButton(onClick = {
                if (input.isNotBlank()) {
                    onAdd(input.trim())
                    input = ""
                }
            }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.reminder_offset_add))
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            offsets.forEach { offset ->
                InputChip(
                    selected = false,
                    onClick = { onRemove(offset) },
                    label = { Text(offset) },
                    trailingIcon = {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.capture_remove_tag), modifier = Modifier.size(16.dp))
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TimesOfDayEditor(
    times: List<String>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit
) {
    var showPicker by remember { mutableStateOf(false) }
    val locale = currentLocale()
    Column(modifier = Modifier.padding(top = 8.dp)) {
        Text(stringResource(R.string.field_rec_times_of_day))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            times.forEach { time ->
                InputChip(
                    selected = false,
                    onClick = { onRemove(time) },
                    label = { Text(formatLocalTime(LocalTime.parse(time), locale)) },
                    trailingIcon = {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.capture_remove_tag), modifier = Modifier.size(16.dp))
                    }
                )
            }
            IconButton(onClick = { showPicker = true }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.field_rec_add_time))
            }
        }
    }
    if (showPicker) {
        AppTimePickerDialog(
            initial = null,
            onDismiss = { showPicker = false },
            onConfirm = { onAdd(it.toString()) }
        )
    }
}
