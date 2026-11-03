package com.jaid.timity.ui

import com.jaid.timity.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jaid.timity.data.TimeEntry
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/** Add or edit a single entry. Out time may be cleared to leave the session open. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryDialog(
    entry: TimeEntry,
    onDismiss: () -> Unit,
    onSave: (TimeEntry) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var date by remember { mutableStateOf(entry.clockIn.toLocalDate()) }
    var inTime by remember { mutableStateOf(entry.clockIn.toLocalDateTime().toLocalTime().withSecond(0).withNano(0)) }
    var outTime by remember { mutableStateOf(entry.clockOut?.toLocalDateTime()?.toLocalTime()?.withSecond(0)?.withNano(0)) }
    var note by rememberSaveable { mutableStateOf(entry.note) }

    var pickDate by remember { mutableStateOf(false) }
    var pickIn by remember { mutableStateOf(false) }
    var pickOut by remember { mutableStateOf(false) }
    val isOpenSession = entry.id != 0L && entry.clockOut == null

    val outBeforeIn = outTime != null && outTime!!.isBefore(inTime)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(stringResource(if (entry.id == 0L) R.string.add_entry else R.string.edit_entry)) },
        text = {
            Column {
                Field(stringResource(R.string.date), date.formatDay(withYear = true)) { pickDate = true }
                Spacer(Modifier.height(8.dp))
                Field(stringResource(R.string.in_time), inTime.formatTime()) { pickIn = true }
                Spacer(Modifier.height(8.dp))
                Field(stringResource(R.string.out_time), outTime?.formatTime() ?: stringResource(if (isOpenSession) R.string.still_clocked_in else R.string.still_open)) { pickOut = true }
                if (outTime != null) {
                    TextButton(onClick = { outTime = null }, modifier = Modifier.padding(top = 4.dp)) {
                        Text(stringResource(R.string.clear_out_time))
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(stringResource(R.string.comment)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (outBeforeIn) {
                    Text(
                        stringResource(R.string.out_after_in),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !outBeforeIn,
                onClick = {
                    onSave(
                        entry.copy(
                            clockIn = date.atTime(inTime).toEpochMillis(),
                            clockOut = outTime?.let { date.atTime(it).toEpochMillis() },
                            note = note.trim(),
                        ),
                    )
                },
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )

    if (pickDate) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    pickDate = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text(stringResource(R.string.cancel)) } },
        ) { DatePicker(state = state) }
    }

    if (pickIn) {
        TimeDialog(initial = inTime, onDismiss = { pickIn = false }) { inTime = it; pickIn = false }
    }
    if (pickOut) {
        TimeDialog(initial = outTime ?: inTime.plusHours(8), onDismiss = { pickOut = false }) { outTime = it; pickOut = false }
    }
}

@Composable
private fun Field(label: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        OutlinedButton(onClick = onClick) { Text(value) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeDialog(initial: LocalTime, onDismiss: () -> Unit, onPick: (LocalTime) -> Unit) {
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = { onPick(LocalTime.of(state.hour, state.minute)) }) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
