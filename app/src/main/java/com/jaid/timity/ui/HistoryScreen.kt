package com.jaid.timity.ui

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
import com.jaid.timity.MainViewModel
import com.jaid.timity.Period
import com.jaid.timity.data.Pay
import com.jaid.timity.data.Settings
import com.jaid.timity.data.TimeEntry
import java.time.LocalDate
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun HistoryScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    val filter by vm.filter.collectAsStateWithLifecycle()
    val entries by vm.historyEntries.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val summary = Pay.summarize(entries, settings)

    // null = dialog closed; id 0 = new entry; otherwise editing that entry.
    var editing by remember { mutableStateOf<TimeEntry?>(null) }

    Column(modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SingleChoiceSegmentedButtonRow(Modifier.weight(1f)) {
                Period.entries.forEachIndexed { i, p ->
                    SegmentedButton(
                        selected = filter.period == p,
                        onClick = { vm.setFilter(filter.copy(period = p)) },
                        shape = SegmentedButtonDefaults.itemShape(i, Period.entries.size),
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    ) { Text(if (p == Period.MONTH) "Month" else "Year") }
                }
            }
            IconButton(onClick = {
                editing = TimeEntry(clockIn = LocalDate.now().atTime(9, 0).toEpochMillis(),
                    clockOut = LocalDate.now().atTime(17, 0).toEpochMillis())
            }) { Icon(Icons.Default.Add, contentDescription = "Add entry") }
        }

        Row(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = { vm.setFilter(filter.previous()) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous")
            }
            val label = when (filter.period) {
                Period.MONTH -> "${Month.of(filter.month).getDisplayName(TextStyle.FULL, Locale.getDefault())} ${filter.year}"
                Period.YEAR -> filter.year.toString()
            }
            Text(label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium)
            IconButton(onClick = { vm.setFilter(filter.next()) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next")
            }
        }

        Summary(summary, settings)
        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        if (entries.isEmpty()) {
            Spacer(Modifier.height(48.dp))
            Text(
                "No entries for this period.",
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(entries, key = { it.id }) { e ->
                    HistoryRow(e, settings, onClick = { editing = e })
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

@Composable
private fun Summary(s: Pay.Summary, settings: Settings) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Stat("Worked", formatHours(s.workedHours))
            Stat("Overtime", formatHours(s.overtimeHours), alignEnd = true)
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Stat("Hourly OT rate", formatMvr(settings.overtimeRate) + "/h")
            Stat("Overtime pay", formatMvr(s.overtimePay), alignEnd = true)
        }
    }
}

@Composable
private fun HistoryRow(e: TimeEntry, settings: Settings, onClick: () -> Unit) {
    val ot = Pay.overtimeMinutes(e, settings)
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                e.clockIn.toLocalDate().formatDay(),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                "${e.clockIn.formatTime()}  –  ${e.clockOut?.formatTime() ?: "open"}",
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
                if (ot > 0) "OT ${formatDuration(ot)} · ${formatMvr(ot / 60.0 * settings.overtimeRate)}" else "no overtime",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
