package com.jaid.timity.ui

import com.jaid.timity.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jaid.timity.HistoryFilter
import com.jaid.timity.MainViewModel
import com.jaid.timity.Period
import com.jaid.timity.data.Pay
import com.jaid.timity.data.Rules
import com.jaid.timity.data.Settings
import com.jaid.timity.data.TimeEntry
import java.time.LocalDate
import java.time.Month
import java.time.format.TextStyle

@Composable
fun HistoryScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    val filter by vm.filter.collectAsStateWithLifecycle()
    val entries by vm.historyEntries.collectAsStateWithLifecycle()
    val rules by vm.rules.collectAsStateWithLifecycle()
    val summary = Pay.summarize(entries, rules)

    // null = dialog closed; id 0 = new entry; otherwise editing that entry.
    var editing by remember { mutableStateOf<TimeEntry?>(null) }

    Column(modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            PeriodToggle(filter, onChange = vm::setFilter, modifier = Modifier.weight(1f))
            IconButton(onClick = {
                editing = TimeEntry(clockIn = LocalDate.now().atTime(9, 0).toEpochMillis(),
                    clockOut = LocalDate.now().atTime(17, 0).toEpochMillis())
            }) { Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_entry)) }
        }

        PeriodSelector(filter, onChange = vm::setFilter)

        Summary(summary, rules.settings)
        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        if (entries.isEmpty()) {
            Spacer(Modifier.height(48.dp))
            Text(
                stringResource(R.string.no_entries),
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(entries, key = { it.id }) { e ->
                    HistoryRow(e, rules, onClick = { editing = e })
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }

    editing?.let { e ->
        EntryDialog(
            entry = e,
            onDismiss = { editing = null },
            onSave = { vm.save(it); editing = null },
            onDelete = if (e.id != 0L) ({ vm.delete(e); editing = null }) else null,
        )
    }
}

/** Previous / label / next row for the month-or-year filter. */
@Composable
fun PeriodSelector(filter: HistoryFilter, onChange: (HistoryFilter) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(onClick = { onChange(filter.previous()) }) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.previous))
        }
        Text(filter.label(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium)
        IconButton(onClick = { onChange(filter.next()) }) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.next))
        }
    }
}

fun HistoryFilter.label(): String = when (period) {
    Period.MONTH -> "${Month.of(month).getDisplayName(TextStyle.FULL, AppLocale.current)} $year"
    Period.YEAR -> year.toString()
}

/** Month / Year toggle. */
@Composable
fun PeriodToggle(filter: HistoryFilter, onChange: (HistoryFilter) -> Unit, modifier: Modifier = Modifier) {
    SingleChoiceSegmentedButtonRow(modifier) {
        Period.entries.forEachIndexed { i, p ->
            SegmentedButton(
                selected = filter.period == p,
                onClick = { onChange(filter.copy(period = p)) },
                shape = SegmentedButtonDefaults.itemShape(i, Period.entries.size),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            ) { Text(stringResource(if (p == Period.MONTH) R.string.month else R.string.year)) }
        }
    }
}

@Composable
private fun Summary(s: Pay.Summary, settings: Settings) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Stat(stringResource(R.string.worked), formatHours(s.workedHours))
            Stat(stringResource(R.string.overtime), formatHours(s.overtimeHours), alignEnd = true)
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Stat(stringResource(R.string.hourly_ot_rate), stringResource(R.string.per_hour, formatMvr(settings.overtimeRate)))
            Stat(stringResource(R.string.overtime_pay), formatMvr(s.overtimePay), alignEnd = true)
        }
    }
}

@Composable
private fun HistoryRow(e: TimeEntry, rules: Rules, onClick: () -> Unit) {
    val ot = Pay.overtimeMinutes(e, rules)
    val holiday = Pay.isHoliday(e, rules)
    val settings = rules.settings
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                e.clockIn.toLocalDate().formatDay() + if (holiday) "  ·  " + stringResource(R.string.holiday) else "",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                "${e.clockIn.formatTime()}  –  ${e.clockOut?.formatTime() ?: stringResource(R.string.open)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                formatDuration(Pay.workedMinutes(e)),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                if (ot > 0) stringResource(R.string.ot_line, formatDuration(ot), formatMvr(ot / 60.0 * settings.overtimeRate))
                else stringResource(R.string.no_overtime),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
