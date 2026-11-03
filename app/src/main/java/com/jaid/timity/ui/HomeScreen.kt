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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jaid.timity.MainViewModel
import com.jaid.timity.data.Pay
import com.jaid.timity.data.TimeEntry
import kotlinx.coroutines.delay
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    val open by vm.openEntry.collectAsStateWithLifecycle()
    val today by vm.todayEntries.collectAsStateWithLifecycle()
    val rules by vm.rules.collectAsStateWithLifecycle()

    // Tick once a second while a session is open so the elapsed time stays live.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(open?.id) {
        while (open != null) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }

    var askNote by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<TimeEntry?>(null) }
    var note by rememberSaveable { mutableStateOf("") }

    val summary = Pay.summarize(today, rules, now)
    val isHoliday = rules.isDayOff(LocalDate.now())
    val periodToday = rules.periodFor(LocalDate.now())

    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(32.dp))
        Text(
            LocalDate.now().formatDay(withYear = true) +
                (if (isHoliday) "  ·  " + stringResource(R.string.holiday) else "") +
                (if (periodToday != null) "  ·  " + stringResource(R.string.period_hours, periodToday.name) else ""),
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
            if (open != null) stringResource(R.string.since, open!!.clockIn.formatTime()) else stringResource(R.string.worked_today),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = { if (open == null) vm.clockIn() else { note = ""; askNote = true } },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Text(stringResource(if (open == null) R.string.clock_in else R.string.clock_out), style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(32.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Stat(stringResource(R.string.worked), formatDuration(summary.workedMinutes))
            Stat(stringResource(R.string.overtime), formatDuration(summary.overtimeMinutes), alignEnd = false)
            Stat(stringResource(R.string.ot_pay), formatMvr(summary.overtimePay), alignEnd = true)
        }
        Spacer(Modifier.height(24.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        LazyColumn(Modifier.fillMaxSize()) {
            items(today, key = { it.id }) { e ->
                EntryRow(e, now, onClick = { editing = e })
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }

    editing?.let { e ->
        EntryDialog(
            entry = e,
            onDismiss = { editing = null },
            onSave = { vm.save(it); editing = null },
            onDelete = { vm.delete(e); editing = null },
        )
    }

    if (askNote) {
        val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { askNote = false },
            sheetState = sheet,
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
                Text(stringResource(R.string.clock_out), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.comment_prompt),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(stringResource(R.string.comment)) },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { vm.clockOut(note); askNote = false },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) { Text(stringResource(R.string.clock_out)) }
                TextButton(
                    onClick = { vm.clockOut(""); askNote = false },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.clock_out_no_comment)) }
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
private fun EntryRow(e: TimeEntry, now: Long, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.in_time) + " " + e.clockIn.formatTime() + "  ·  " +
                    (e.clockOut?.let { stringResource(R.string.out_time) + " " + it.formatTime() } ?: stringResource(R.string.still_clocked_in)),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                formatDuration(Pay.workedMinutes(e, now)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        NoteText(e.note)
    }
}

/** A session's comment in secondary italic text; renders nothing when blank. */
@Composable
fun NoteText(note: String, modifier: Modifier = Modifier) {
    if (note.isBlank()) return
    Text(
        note,
        style = MaterialTheme.typography.bodyMedium,
        fontStyle = FontStyle.Italic,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(top = 4.dp),
    )
}
