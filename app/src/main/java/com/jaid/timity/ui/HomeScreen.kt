package com.jaid.timity.ui

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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jaid.timity.MainViewModel
import com.jaid.timity.data.Pay
import com.jaid.timity.data.TimeEntry
import kotlinx.coroutines.delay
import java.time.LocalDate

@Composable
fun HomeScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    val open by vm.openEntry.collectAsStateWithLifecycle()
    val today by vm.todayEntries.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val holidays by vm.holidayDates.collectAsStateWithLifecycle()

    // Tick once a second while a session is open so the elapsed time stays live.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(open?.id) {
        while (open != null) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }

    val summary = Pay.summarize(today, settings, holidays, now)
    val isHoliday = Pay.isDayOff(LocalDate.now(), settings, holidays)

    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(32.dp))
        Text(
            LocalDate.now().formatDay(withYear = true) + if (isHoliday) "  ·  Holiday" else "",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        val elapsed = open?.let { Pay.workedMinutes(it, now) } ?: summary.workedMinutes
        Text(
            formatDuration(elapsed),
            fontSize = 56.sp,
            fontWeight = FontWeight.Light,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            if (open != null) "since ${open!!.clockIn.formatTime()}" else "worked today",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = { if (open == null) vm.clockIn() else vm.clockOut() },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Text(if (open == null) "Clock in" else "Clock out", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(32.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Stat("Worked", formatDuration(summary.workedMinutes))
            Stat("Overtime", formatDuration(summary.overtimeMinutes), alignEnd = false)
            Stat("OT pay", formatMvr(summary.overtimePay), alignEnd = true)
        }
        Spacer(Modifier.height(24.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        LazyColumn(Modifier.fillMaxSize()) {
            items(today, key = { it.id }) { e ->
                EntryRow(e, now)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
fun Stat(label: String, value: String, alignEnd: Boolean = false) {
    Column(horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
    }
}

@Composable
private fun EntryRow(e: TimeEntry, now: Long) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "${e.clockIn.formatTime()}  –  ${e.clockOut?.formatTime() ?: "now"}",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            formatDuration(Pay.workedMinutes(e, now)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
