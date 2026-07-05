package com.shuremind.ui.meter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.shuremind.R
import com.shuremind.ui.common.currentLocale
import com.shuremind.ui.common.formatEpochMillis
import java.time.ZoneId
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeterReadingsScreen(
    viewModel: MeterReadingsViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val input by viewModel.input.collectAsState()
    val locale = currentLocale()
    val zone = remember { ZoneId.systemDefault() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.meter_readings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.nav_back))
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = input.meterName,
                    onValueChange = viewModel::setMeterNameInput,
                    label = { Text(stringResource(R.string.meter_name_hint)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = input.value,
                    onValueChange = viewModel::setValueInput,
                    label = { Text(stringResource(R.string.meter_value_hint)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                val seedTitle = stringResource(R.string.meter_seed_task_title, input.meterName.trim())
                IconButton(onClick = { viewModel.addReading(seedTitle) }, enabled = input.canSave) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.meter_add_reading))
                }
            }

            if (uiState.groups.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.meter_empty))
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    uiState.groups.forEach { group ->
                        item {
                            Text(
                                text = group.meterName,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                        items(group.joinedTasks, key = { "task-" + it.task.id }) { joined ->
                            val deltaText = joined.deltaSinceLastDone?.let { formatNumber(it, locale) }
                            Text(
                                text = if (deltaText != null) {
                                    stringResource(R.string.meter_task_delta_value, joined.task.title, deltaText)
                                } else {
                                    joined.task.title
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                            )
                        }
                        items(group.readings, key = { "reading-" + it.id }) { reading ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(formatEpochMillis(reading.recordedAt, zone, locale), style = MaterialTheme.typography.bodySmall)
                                Text(formatNumber(reading.value, locale), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
                    }
                }
            }
        }
    }
}

private fun formatNumber(value: Double, locale: Locale): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else String.format(locale, "%.1f", value)
