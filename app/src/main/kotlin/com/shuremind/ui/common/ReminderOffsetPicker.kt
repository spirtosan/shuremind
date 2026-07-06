package com.shuremind.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.shuremind.R

/** D-37: formats a stored offset ISO string for display; the caller shows the raw string + error styling if this returns null. */
@Composable
private fun reminderOffsetDisplayText(value: ReminderOffsetValue): String = when (value) {
    ReminderOffsetValue.AtDue -> stringResource(R.string.reminder_offset_at_due)
    is ReminderOffsetValue.Before -> when (value.unit) {
        OffsetUnit.MINUTES -> pluralStringResource(R.plurals.reminder_offset_minutes_before, value.amount, value.amount)
        OffsetUnit.HOURS -> pluralStringResource(R.plurals.reminder_offset_hours_before, value.amount, value.amount)
        OffsetUnit.DAYS -> pluralStringResource(R.plurals.reminder_offset_days_before, value.amount, value.amount)
        OffsetUnit.WEEKS -> pluralStringResource(R.plurals.reminder_offset_weeks_before, value.amount, value.amount)
    }
}

/**
 * D-37: replaces the old free-text ISO field. Shows existing offsets as chips (formatted, e.g.
 * "2 weeks before due"; unparseable stored values show the raw string in an error style, tap to
 * remove and re-add — never crashes) plus a structured add row (amount + unit + before-due/at-due).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReminderOffsetEditor(
    offsets: List<String>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            offsets.forEach { iso ->
                val parsed = remember(iso) { ReminderOffsetFormat.parse(iso) }
                InputChip(
                    selected = false,
                    onClick = { onRemove(iso) },
                    label = {
                        Text(
                            if (parsed != null) reminderOffsetDisplayText(parsed)
                            else stringResource(R.string.reminder_offset_invalid, iso)
                        )
                    },
                    trailingIcon = {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.capture_remove_tag), modifier = Modifier.size(16.dp))
                    },
                    colors = if (parsed == null) {
                        InputChipDefaults.inputChipColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            labelColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    } else {
                        InputChipDefaults.inputChipColors()
                    }
                )
            }
        }
        ReminderOffsetAddRow(onAdd = onAdd, modifier = Modifier.padding(top = 8.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderOffsetAddRow(onAdd: (String) -> Unit, modifier: Modifier = Modifier) {
    var atDue by remember { mutableStateOf(false) }
    var amountText by remember { mutableStateOf("1") }
    var unit by remember { mutableStateOf(OffsetUnit.DAYS) }
    var unitMenuExpanded by remember { mutableStateOf(false) }

    val amount = amountText.toIntOrNull()
    val amountValid = amount != null && ReminderOffsetFormat.isValidAmount(amount, unit)
    val canAdd = atDue || amountValid

    Column(modifier = modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !atDue, onClick = { atDue = false }, label = { Text(stringResource(R.string.reminder_offset_mode_before)) })
            FilterChip(selected = atDue, onClick = { atDue = true }, label = { Text(stringResource(R.string.reminder_offset_mode_at_due)) })
        }
        if (!atDue) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { input -> amountText = input.filter(Char::isDigit).take(3) },
                    label = { Text(stringResource(R.string.reminder_offset_amount_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    isError = amount != null && !amountValid,
                    modifier = Modifier.width(96.dp)
                )
                ExposedDropdownMenuBox(
                    expanded = unitMenuExpanded,
                    onExpandedChange = { unitMenuExpanded = it },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = stringResource(unit.labelRes()),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.reminder_offset_unit_label)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = unitMenuExpanded) },
                        modifier = Modifier
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = unitMenuExpanded,
                        onDismissRequest = { unitMenuExpanded = false }
                    ) {
                        OffsetUnit.entries.forEach { candidate ->
                            DropdownMenuItem(
                                text = { Text(stringResource(candidate.labelRes())) },
                                onClick = { unit = candidate; unitMenuExpanded = false }
                            )
                        }
                    }
                }
            }
            Text(
                text = stringResource(R.string.reminder_offset_before_due_suffix),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp)
            )
            if (amount != null && !amountValid) {
                Text(
                    text = stringResource(R.string.reminder_offset_error_range),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
        Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                enabled = canAdd,
                onClick = {
                    val value = if (atDue) ReminderOffsetValue.AtDue else ReminderOffsetValue.Before(amount!!, unit)
                    onAdd(ReminderOffsetFormat.toIso(value))
                    amountText = "1"
                    atDue = false
                }
            ) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.reminder_offset_add))
            }
            Text(stringResource(R.string.reminder_offset_add))
        }
    }
}
