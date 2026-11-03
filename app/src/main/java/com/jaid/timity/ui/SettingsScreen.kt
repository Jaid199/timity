package com.jaid.timity.ui

import com.jaid.timity.MainActivity
import com.jaid.timity.R
import androidx.compose.ui.res.stringResource
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
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
import com.jaid.timity.data.OvertimeMode
import com.jaid.timity.data.Ramadan
import com.jaid.timity.data.ThemeMode
import com.jaid.timity.data.WorkPeriod
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun SettingsScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val holidays by vm.holidays.collectAsStateWithLifecycle()
    val periods by vm.periods.collectAsStateWithLifecycle()
    val rules by vm.rules.collectAsStateWithLifecycle()
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
        Text(stringResource(R.string.settings), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = rateText,
            onValueChange = { v ->
                rateText = v
                v.toDoubleOrNull()?.let { if (it >= 0) vm.setOvertimeRate(it) }
            },
            label = { Text(stringResource(R.string.overtime_rate_label)) },
            supportingText = { Text(stringResource(R.string.overtime_rate_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            isError = rateText.isNotEmpty() && rateText.toDoubleOrNull() == null,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        SectionLabel(stringResource(R.string.overtime_mode))
        Spacer(Modifier.height(8.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            OvertimeMode.entries.forEachIndexed { i, mode ->
                SegmentedButton(
                    selected = settings.overtimeMode == mode,
                    onClick = { vm.setOvertimeMode(mode) },
                    shape = SegmentedButtonDefaults.itemShape(i, OvertimeMode.entries.size),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                ) {
                    Text(
                        stringResource(
                            when (mode) {
                                OvertimeMode.OUTSIDE_WORKING_HOURS -> R.string.overtime_mode_outside
                                OvertimeMode.BEYOND_STANDARD_HOURS -> R.string.overtime_mode_beyond
                            },
                        ),
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Hint(
            stringResource(
                when (settings.overtimeMode) {
                    OvertimeMode.OUTSIDE_WORKING_HOURS -> R.string.overtime_mode_outside_hint
                    OvertimeMode.BEYOND_STANDARD_HOURS -> R.string.overtime_mode_beyond_hint
                },
            ),
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = hoursText,
            onValueChange = { v ->
                hoursText = v
                v.toDoubleOrNull()?.let { if (it in 0.0..24.0) vm.setStandardHours(it) }
            },
            label = { Text(stringResource(R.string.standard_hours_label)) },
            supportingText = { Text(stringResource(R.string.standard_hours_hint)) },
            singleLine = true,
            enabled = settings.overtimeMode == OvertimeMode.BEYOND_STANDARD_HOURS,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            isError = hoursText.isNotEmpty() && hoursText.toDoubleOrNull() == null,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(24.dp))
        var editingPeriod by remember { mutableStateOf<WorkPeriod?>(null) }
        val ramadanName = stringResource(R.string.ramadan)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SectionLabel(stringResource(R.string.working_hours))
            Spacer(Modifier.weight(1f))
            IconButton(onClick = {
                // Pre-fill from the synced calendar when it has Ramadan; otherwise a placeholder range.
                val range = Ramadan.upcoming(holidays)
                    ?: Ramadan.Range(LocalDate.now(), LocalDate.now().plusDays(29))
                editingPeriod = WorkPeriod(
                    name = ramadanName,
                    startEpochDay = range.start.toEpochDay(),
                    endEpochDay = range.end.toEpochDay(),
                    workStart = 9 * 60,
                    workEnd = 13 * 60,
                    standardHours = 4.0,
                )
            }) { Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_period)) }
        }
        Hint(stringResource(R.string.working_hours_hint))
        Spacer(Modifier.height(8.dp))
        var pickStart by rememberSaveable { mutableStateOf(false) }
        var pickEnd by rememberSaveable { mutableStateOf(false) }
        val start = LocalTime.ofSecondOfDay(settings.workStart * 60L)
        val end = LocalTime.ofSecondOfDay(settings.workEnd * 60L)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { pickStart = true }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.from_time, start.formatTime())) }
            OutlinedButton(onClick = { pickEnd = true }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.to_time, end.formatTime())) }
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
        Spacer(Modifier.height(8.dp))
        val today = LocalDate.now()
        val active = rules.periodFor(today)
        Hint(
            if (active == null) stringResource(R.string.today_default_hours, start.formatTime(), end.formatTime())
            else stringResource(
                R.string.today_period_hours,
                active.name,
                LocalTime.ofSecondOfDay(active.workStart * 60L).formatTime(),
                LocalTime.ofSecondOfDay(active.workEnd * 60L).formatTime(),
                active.end.formatDate(),
            ),
        )
        periods.forEach { p ->
            PeriodRow(
                p,
                onClick = { editingPeriod = p },
                onToggle = { vm.savePeriod(p.copy(enabled = it)) },
                onDelete = { vm.deletePeriod(p) },
            )
        }
        editingPeriod?.let { p ->
            WorkPeriodDialog(
                period = p,
                onDismiss = { editingPeriod = null },
                onDelete = if (p.id != 0L) ({ vm.deletePeriod(p); editingPeriod = null }) else null,
            ) { vm.savePeriod(it); editingPeriod = null }
        }

        Spacer(Modifier.height(32.dp))
        SectionLabel(stringResource(R.string.appearance))
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
                    Text(
                        stringResource(
                            when (mode) {
                                ThemeMode.SYSTEM -> R.string.theme_system
                                ThemeMode.LIGHT -> R.string.theme_light
                                ThemeMode.DARK -> R.string.theme_dark
                            },
                        ),
                    )
                }
            }
        }

        Spacer(Modifier.height(32.dp))
        SectionLabel(stringResource(R.string.language))
        Spacer(Modifier.height(8.dp))
        val currentLang = MainActivity.currentLanguage()
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf("en" to R.string.lang_en, "dv" to R.string.lang_dv).forEachIndexed { i, (tag, label) ->
                SegmentedButton(
                    selected = currentLang == tag,
                    onClick = { if (currentLang != tag) MainActivity.setLanguage(tag) },
                    shape = SegmentedButtonDefaults.itemShape(i, 2),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                ) { Text(stringResource(label)) }
            }
        }

        Spacer(Modifier.height(32.dp))
        SectionLabel(stringResource(R.string.weekly_days_off))
        Spacer(Modifier.height(4.dp))
        Hint(stringResource(R.string.weekly_days_off_hint))
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DayOfWeek.entries.forEach { day ->
                val on = day in settings.weekendDays
                DayToggle(
                    label = day.getDisplayName(TextStyle.SHORT, AppLocale.current).take(2),
                    selected = on,
                    onClick = { vm.setWeekendDays(if (on) settings.weekendDays - day else settings.weekendDays + day) },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Spacer(Modifier.height(32.dp))
        SectionLabel(stringResource(R.string.holidays))
        Spacer(Modifier.height(4.dp))
        Hint(stringResource(R.string.holidays_hint))
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = keyText,
            onValueChange = { keyText = it; vm.setHolidayApiKey(it) },
            label = { Text(stringResource(R.string.api_key)) },
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
                Text(stringResource(R.string.sync_years, year, year + 1))
            }
        }
        Spacer(Modifier.height(8.dp))
        // Quick-add: a holiday, a sick day or a day off, each pre-filled and then editable.
        var adding by rememberSaveable { mutableStateOf<String?>(null) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Holiday.CUSTOM_TYPES.forEach { type ->
                OutlinedButton(onClick = { adding = type }, modifier = Modifier.weight(1f)) {
                    Text("+ " + typeLabel(type), maxLines = 1)
                }
            }
        }
        adding?.let { type ->
            val defaultName = if (type == Holiday.TYPE_CUSTOM) "" else typeLabel(type)
            HolidayDialog(
                title = stringResource(R.string.add_holiday),
                initialName = defaultName,
                initialDate = LocalDate.now(),
                initialType = type,
                onDismiss = { adding = null },
            ) { date, name, t -> vm.addHoliday(date, name, t); adding = null }
        }
        val status = when (val s = syncState) {
            SyncState.Idle -> if (settings.holidaysSyncedAt > 0L)
                stringResource(R.string.last_synced, settings.holidaysSyncedAt.toLocalDate().formatDay(withYear = true)) else null
            SyncState.Running -> stringResource(R.string.fetching_holidays)
            is SyncState.Done -> stringResource(R.string.sync_done, s.added, s.total)
            SyncState.MissingKey -> stringResource(R.string.sync_missing_key)
            is SyncState.Failed -> stringResource(R.string.sync_failed, s.message)
        }
        if (status != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                status,
                style = MaterialTheme.typography.bodySmall,
                color = if (syncState is SyncState.Failed || syncState is SyncState.MissingKey) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(12.dp))
        var query by rememberSaveable { mutableStateOf("") }
        if (holidays.isNotEmpty()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(stringResource(R.string.search_holidays)) },
                singleLine = true,
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) { Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.cancel)) }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        if (holidays.isEmpty()) {
            Spacer(Modifier.height(16.dp))
            Hint(stringResource(R.string.no_holidays))
        }
        val shown = remember(holidays, query) { holidays.filter { it.matches(query) } }
        if (holidays.isNotEmpty() && shown.isEmpty()) {
            Spacer(Modifier.height(16.dp))
            Hint(stringResource(R.string.no_matching_holidays))
        }
        var editing by remember { mutableStateOf<Holiday?>(null) }
        shown.forEach { h ->
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
                title = stringResource(R.string.edit_holiday),
                initialName = h.name,
                initialDate = h.date,
                initialType = h.type,
                typeEditable = h.source == Holiday.SOURCE_CUSTOM,
                onDismiss = { editing = null },
                onDelete = { vm.deleteHoliday(h); editing = null },
            ) { date, name, t -> vm.updateHoliday(h, date, name, t); editing = null }
        }
        Spacer(Modifier.height(32.dp))
    }
}


@Composable
private fun PeriodRow(p: WorkPeriod, onClick: () -> Unit, onToggle: (Boolean) -> Unit, onDelete: () -> Unit) {
    val today = LocalDate.now()
    val status = stringResource(
        when {
            !p.enabled -> R.string.period_status_off
            p.contains(today) -> R.string.period_status_active
            today.isBefore(p.start) -> R.string.period_status_upcoming
            else -> R.string.period_status_finished
        },
    )
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "${p.name}  ·  $status",
                style = MaterialTheme.typography.bodyLarge,
                color = if (p.enabled) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "${p.start.formatDate()} – ${p.end.formatDate()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "${LocalTime.ofSecondOfDay(p.workStart * 60L).formatTime()} – ${LocalTime.ofSecondOfDay(p.workEnd * 60L).formatTime()}" +
                    "  ·  " + stringResource(R.string.standard_day, p.standardHours.trimmed()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = p.enabled,
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
private fun WorkPeriodDialog(
    period: WorkPeriod,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)?,
    onSave: (WorkPeriod) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(period.name) }
    var start by remember { mutableStateOf(period.start) }
    var end by remember { mutableStateOf(period.end) }
    var workStart by remember { mutableStateOf(LocalTime.ofSecondOfDay(period.workStart * 60L)) }
    var workEnd by remember { mutableStateOf(LocalTime.ofSecondOfDay(period.workEnd * 60L)) }
    var hoursText by rememberSaveable { mutableStateOf(period.standardHours.trimmed()) }

    var pickStart by rememberSaveable { mutableStateOf(false) }
    var pickEnd by rememberSaveable { mutableStateOf(false) }
    var pickWorkStart by rememberSaveable { mutableStateOf(false) }
    var pickWorkEnd by rememberSaveable { mutableStateOf(false) }

    val hours = hoursText.toDoubleOrNull()
    val rangeOk = !end.isBefore(start)
    val valid = name.isNotBlank() && rangeOk && hours != null && hours in 0.0..24.0

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(stringResource(if (period.id == 0L) R.string.add_period else R.string.edit_period)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text(stringResource(R.string.name)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                DialogField(stringResource(R.string.from_date), start.formatDate()) { pickStart = true }
                Spacer(Modifier.height(8.dp))
                DialogField(stringResource(R.string.to_date), end.formatDate()) { pickEnd = true }
                if (!rangeOk) {
                    Text(stringResource(R.string.end_before_start), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(8.dp))
                DialogField(stringResource(R.string.work_from), workStart.formatTime()) { pickWorkStart = true }
                Spacer(Modifier.height(8.dp))
                DialogField(stringResource(R.string.work_to), workEnd.formatTime()) { pickWorkEnd = true }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = hoursText, onValueChange = { hoursText = it },
                    label = { Text(stringResource(R.string.standard_hours_label)) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = hoursText.isNotEmpty() && (hours == null || hours !in 0.0..24.0),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                onSave(
                    period.copy(
                        name = name.trim(),
                        startEpochDay = start.toEpochDay(),
                        endEpochDay = end.toEpochDay(),
                        workStart = workStart.toSecondOfDay() / 60,
                        workEnd = workEnd.toSecondOfDay() / 60,
                        standardHours = hours ?: period.standardHours,
                    ),
                )
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )

    if (pickStart) DateDialog(start, onDismiss = { pickStart = false }) { start = it; if (end.isBefore(it)) end = it; pickStart = false }
    if (pickEnd) DateDialog(end, onDismiss = { pickEnd = false }) { end = it; pickEnd = false }
    if (pickWorkStart) TimeDialog(initial = workStart, onDismiss = { pickWorkStart = false }) { workStart = it; pickWorkStart = false }
    if (pickWorkEnd) TimeDialog(initial = workEnd, onDismiss = { pickWorkEnd = false }) { workEnd = it; pickWorkEnd = false }
}

@Composable
private fun DialogField(label: String, value: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        OutlinedButton(onClick = onClick) { Text(value) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateDialog(initial: LocalDate, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) } ?: onDismiss()
            }) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    ) { DatePicker(state = state) }
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
                "${h.date.formatDay(withYear = true)}  ·  ${typeLabel(h.type)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (h.source == Holiday.SOURCE_CUSTOM) {
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete), tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
    initialType: String,
    typeEditable: Boolean = true,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
    onSave: (LocalDate, String, String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var date by remember { mutableStateOf(initialDate) }
    var type by rememberSaveable { mutableStateOf(initialType) }
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
                    label = { Text(stringResource(R.string.name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.date), style = MaterialTheme.typography.bodyLarge)
                    OutlinedButton(onClick = { pickDate = true }) { Text(date.formatDay(withYear = true)) }
                }
                if (typeEditable) {
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.kind), style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(6.dp))
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        Holiday.CUSTOM_TYPES.forEachIndexed { i, t ->
                            SegmentedButton(
                                selected = type == t,
                                onClick = { type = t },
                                shape = SegmentedButtonDefaults.itemShape(i, Holiday.CUSTOM_TYPES.size),
                                colors = SegmentedButtonDefaults.colors(
                                    activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                ),
                            ) { Text(typeLabel(t), maxLines = 1) }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onSave(date, name, type) }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )

    if (pickDate) DateDialog(date, onDismiss = { pickDate = false }) { date = it; pickDate = false }
}

/** Localised label for a holiday type; Calendarific's own type names are shown as they come. */
@Composable
private fun typeLabel(type: String): String = when (type) {
    Holiday.TYPE_CUSTOM -> stringResource(R.string.type_custom)
    Holiday.TYPE_SICK -> stringResource(R.string.type_sick)
    Holiday.TYPE_OFF -> stringResource(R.string.type_off)
    else -> type
}

/** Case-insensitive match on name, type, or the date in a few spellings ("3 Nov", "2026-11-03", "nov 2026"). */
private fun Holiday.matches(query: String): Boolean {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return true
    val d = date
    val haystack = listOf(name, type, d.toString(), d.formatDay(withYear = true), d.formatDate(), d.formatShortDay())
    return haystack.any { it.lowercase().contains(q) }
}

/** 8.0 -> "8", 12.5 -> "12.5". */
private fun Double.trimmed(): String =
    if (this == Math.floor(this)) String.format(Locale.US, "%.0f", this) else String.format(Locale.US, "%s", this)
