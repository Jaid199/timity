package com.jaid.timity.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jaid.timity.MainViewModel
import com.jaid.timity.data.ThemeMode
import java.util.Locale

@Composable
fun SettingsScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    val settings by vm.settings.collectAsStateWithLifecycle()

    var rateText by rememberSaveable { mutableStateOf("") }
    var hoursText by rememberSaveable { mutableStateOf("") }
    var seeded by rememberSaveable { mutableStateOf(false) }

    // Seed the text fields from stored values once; after that the fields drive the store.
    LaunchedEffect(settings) {
        if (!seeded) {
            rateText = settings.overtimeRate.trimmed()
            hoursText = settings.standardHours.trimmed()
            seeded = true
        }
    }

    Column(modifier.fillMaxSize().padding(horizontal = 24.dp)) {
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
            supportingText = { Text("Paid for every hour beyond the standard day.") },
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
        Text("Appearance", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    }
}

/** 8.0 -> "8", 12.5 -> "12.5". */
private fun Double.trimmed(): String =
    if (this == Math.floor(this)) String.format(Locale.US, "%.0f", this) else String.format(Locale.US, "%s", this)
