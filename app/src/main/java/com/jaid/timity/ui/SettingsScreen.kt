package com.jaid.timity.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jaid.timity.MainViewModel
import com.jaid.timity.MainViewModel.SyncState
import com.jaid.timity.data.Holiday
import com.jaid.timity.data.ThemeMode
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun SettingsScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val holidays by vm.holidays.collectAsStateWithLifecycle()
    val syncState by vm.syncState.collectAsStateWithLifecycle()

    var rateText by rememberSaveable { mutableStateOf("") }
    var hoursText by rememberSaveable { mutableStateOf("") }
    var keyText by rememberSaveable { mutableStateOf("") }
    var seeded by rememberSaveable { mutableStateOf(false) }

    // Seed the text fields from stored values once; after that the fields drive the store.
    LaunchedEffect(settings) {
        if (!seeded) {
            rateText = settings.overtimeRate.trimmed()
            hoursText = settings.standardHours.trimmed()
            keyText = settings.holidayApiKey
            seeded = true
        }
    }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
        Spacer(Modifier.height(32.dp))
        Text("Settings", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = rateText,
            onValueChange = { v ->
                rateText = v
                v.toDoubleOrNull()?.let { if (it >= 0) vm.setOvertimeRate(it) }
            },
            label = { Text("Overtime rate (MVR per hour)") },
            supportingText = { Text("Paid for every hour beyond the standard day, and for every hour on a holiday.") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            isError = rateText.isNotEmpty() && rateText.toDoubleOrNull() == null,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = hoursText,
            onValueChange = { v ->
                hoursText = v
                v.toDoubleOrNull()?.let { if (it in 0.0..24.0) vm.setStandardHours(it) }
            },
            label = { Text("Standard hours per day") },
            supportingText = { Text("Time worked past this counts as overtime.") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            isError = hoursText.isNotEmpty() && hoursText.toDoubleOrNull() == null,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(32.dp))
        SectionLabel("Working hours")
        Spacer(Modifier.height(4.dp))
        Hint("Reports split time into inside and outside this window.")
        Spacer(Modifier.height(8.dp))
        var pickStart by rememberSaveable { mutableStateOf(false) }
        var pickEnd by rememberSaveable { mutableStateOf(false) }
        val start = LocalTime.ofSecondOfDay(settings.workStart * 60L)
        val end = LocalTime.ofSecondOfDay(settings.workEnd * 60L)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { pickStart = true }, modifier = Modifier.weight(1f)) { Text("From ${start.formatTime()}") }
            OutlinedButton(onClick = { pickEnd = true }, modifier = Modifier.weight(1f)) { Text("To ${end.formatTime()}") }
        }
        if (pickStart) {
            TimeDialog(initial = start, onDismiss = { pickStart = false }) {
                vm.setWorkWindow(it.toSecondOfDay() / 60, settings.workEnd); pickStart = false
            }
        }
        if (pickEnd) {
            TimeDialog(initial = end, onDismiss = { pickEnd = false }) {
                vm.setWorkWindow(settings.workStart, it.toSecondOfDay() / 60); pickEnd = false
            }
        }

        Spacer(Modifier.height(32.dp))
        SectionLabel("Appearance")
        Spacer(Modifier.height(8.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            ThemeMode.entries.forEachIndexed { i, mode ->
                SegmentedButton(
                    selected = settings.theme == mode,
                    onClick = { vm.setTheme(mode) },
                    shape = SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                ) {
                    Text(mode.name.lowercase().replaceFirstChar { it.titlecase() })
                }
            }
        }

        Spacer(Modifier.height(32.dp))
        SectionLabel("Weekly days off")
        Spacer(Modifier.height(4.dp))
        Hint("Every hour worked on these days is overtime.")
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DayOfWeek.entries.forEach { day ->
                val on = day in settings.weekendDays
                DayToggle(
                    label = day.getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(2),
                    selected = on,
                    onClick = { vm.setWeekendDays(if (on) settings.weekendDays - day else settings.weekendDays + day) },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Spacer(Modifier.height(32.dp))
        SectionLabel("Holidays")
        Spacer(Modifier.height(4.dp))
        Hint("Every hour worked on an enabled holiday is overtime. Defaults come from Calendarific for the Maldives; switch any off or add your own.")
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = keyText,
            onValueChange = { keyText = it; vm.setHolidayApiKey(it) },
            label = { Text("Calendarific API key") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        val year = LocalDate.now().year
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = vm::syncHolidays,
                enabled = syncState !is SyncState.Running && keyText.isNotBlank(),
            ) {
                if (syncState is SyncState.Running) {
                    CircularProgressIndicator(Modifier.width(18.dp).height(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(8.dp))
                }
                Text("Sync $year–${year + 1}")
            }
            Spacer(Modifier.width(12.dp))
            var showAdd by rememberSaveable { mutableStateOf(false) }
            TextButton(onClick = { showAdd = true }) { Text("Add holiday") }
            if (showAdd) {
                HolidayDialog(
                    title = "Add holiday",
                    initialName = "",
                    initialDate = LocalDate.now(),
                    onDismiss = { showAdd = false },
                ) { date, name -> vm.addHoliday(date, name); showAdd = false }
            }
        }
        val status = when (val s = syncState) {
            SyncState.Idle -> if (settings.holidaysSyncedAt > 0L)
                "Last synced ${settings.holidaysSyncedAt.toLocalDate().formatDay(withYear = true)}" else null
            SyncState.Running -> "Fetching holidays…"
            is SyncState.Done -> "Synced: ${s.added} new of ${s.total} holidays."
            is SyncState.Failed -> "Sync failed: ${s.message}"
        }
        if (status != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                status,
                style = MaterialTheme.typography.bodySmall,
                color = if (syncState is SyncState.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        if (holidays.isEmpty()) {
            Spacer(Modifier.height(16.dp))
            Hint("No holidays yet. Enter an API key and sync, or add one manually.")
        }
        var editing by remember { mutableStateOf<Holiday?>(null) }
        holidays.forEach { h ->
            HolidayRow(
                h,
                onClick = { editing = h },
                onToggle = { vm.setHolidayEnabled(h, it) },
                onDelete = { vm.deleteHoliday(h) },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        editing?.let { h ->
            HolidayDialog(
                title = "Edit holiday",
                initialName = h.name,
                initialDate = h.date,
                onDismiss = { editing = null },
                onDelete = { vm.deleteHoliday(h); editing = null },
            ) { date, name -> vm.updateHoliday(h, date, name); editing = null }
        }
        Spacer(Modifier.height(32.dp))
    }
}

/** A compact selectable box; Material's FilterChip is too wide for seven in a row on a phone. */
@Composable
private fun DayToggle(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier
            .height(40.dp)
            .clip(shape)
            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.background)
            .border(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
        )
    }
}

@Composable
private fun SectionLabel(text: String) =
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun Hint(text: String) =
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

private val holidayDateFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.getDefault())

@Composable
private fun HolidayRow(h: Holiday, onClick: () -> Unit, onToggle: (Boolean) -> Unit, onDelete: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                h.name,
                style = MaterialTheme.typography.bodyLarge,
                color = if (h.enabled) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "${h.date.format(holidayDateFmt)}  ·  ${h.type}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (h.source == Holiday.SOURCE_CUSTOM) {
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(
            checked = h.enabled,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
            ),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HolidayDialog(
    title: String,
    initialName: String,
    initialDate: LocalDate,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
    onSave: (LocalDate, String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var date by remember { mutableStateOf(initialDate) }
    var pickDate by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Date", style = MaterialTheme.typography.bodyLarge)
                    OutlinedButton(onClick = { pickDate = true }) { Text(date.formatDay(withYear = true)) }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onSave(date, name) }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
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
                    state.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    pickDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("Cancel") } },
        ) { DatePicker(state = state) }
    }
}

/** 8.0 -> "8", 12.5 -> "12.5". */
private fun Double.trimmed(): String =
    if (this == Math.floor(this)) String.format(Locale.US, "%.0f", this) else String.format(Locale.US, "%s", this)
