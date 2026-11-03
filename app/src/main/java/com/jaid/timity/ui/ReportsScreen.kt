package com.jaid.timity.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jaid.timity.MainViewModel
import com.jaid.timity.Period
import com.jaid.timity.data.Pay
import com.jaid.timity.data.Settings
import com.jaid.timity.data.TimeEntry
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One row of the breakdown list: a day in month view, a month in year view. */
private data class Bucket(val label: String, val split: Pay.Split, val overtimePay: Double)

@Composable
fun ReportsScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    val filter by vm.filter.collectAsStateWithLifecycle()
    val entries by vm.historyEntries.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val holidays by vm.holidayDates.collectAsStateWithLifecycle()

    val summary = Pay.summarize(entries, settings, holidays)
    val split = entries.fold(Pay.Split(0, 0)) { acc, e -> acc + Pay.split(e, settings) }
    val daysWorked = entries.map { it.clockIn.toLocalDate() }.distinct().size
    val buckets = buckets(entries, settings, holidays, filter.period)

    val insideColor = MaterialTheme.colorScheme.onBackground
    val outsideColor = MaterialTheme.colorScheme.outline

    LazyColumn(modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        item {
            Spacer(Modifier.height(16.dp))
            PeriodToggle(filter, onChange = vm::setFilter, modifier = Modifier.fillMaxWidth())
            PeriodSelector(filter, onChange = vm::setFilter)
            Spacer(Modifier.height(8.dp))

            // Hero number: overtime pay.
            Text("Overtime pay", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                formatMvr(summary.overtimePay),
                fontSize = 40.sp,
                fontWeight = FontWeight.Light,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                "${formatHours(summary.overtimeHours)} overtime at ${formatMvr(settings.overtimeRate)}/h",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Stat("Total worked", formatDuration(summary.workedMinutes))
                Stat("Days", daysWorked.toString())
                Stat(
                    "Avg / day",
                    if (daysWorked == 0) "–" else formatDuration(summary.workedMinutes / daysWorked),
                    alignEnd = true,
                )
            }
            Spacer(Modifier.height(28.dp))

            // Inside vs outside working hours.
            val window = "${LocalTime.ofSecondOfDay(settings.workStart * 60L).formatTime()} – " +
                LocalTime.ofSecondOfDay(settings.workEnd * 60L).formatTime()
            Text("Working hours $window", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            SplitBar(split, insideColor, outsideColor, height = 12.dp)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                LegendStat("Within hours", split.insideMinutes, split.total, insideColor)
                LegendStat("Outside hours", split.outsideMinutes, split.total, outsideColor, alignEnd = true)
            }
            Spacer(Modifier.height(28.dp))

            Text(
                if (filter.period == Period.MONTH) "By day" else "By month",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            if (buckets.isEmpty()) {
                Spacer(Modifier.height(32.dp))
                Text(
                    "No entries for this period.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        val maxMinutes = buckets.maxOfOrNull { it.split.total }?.coerceAtLeast(1) ?: 1
        items(buckets, key = { it.label }) { b ->
            BucketRow(b, maxMinutes, insideColor, outsideColor)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

/** A single horizontal bar split into inside / outside segments with a 2dp surface gap between them. */
@Composable
private fun SplitBar(
    split: Pay.Split,
    insideColor: Color,
    outsideColor: Color,
    height: androidx.compose.ui.unit.Dp,
    widthFraction: Float = 1f,
) {
    val total = split.total.coerceAtLeast(1)
    val inside = split.insideMinutes.toFloat() / total
    val outside = split.outsideMinutes.toFloat() / total
    Row(
        Modifier.fillMaxWidth(widthFraction).height(height).clip(RoundedCornerShape(4.dp)),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (split.total == 0L) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHigh))
        } else {
            if (inside > 0f) Box(Modifier.weight(inside).fillMaxSize().background(insideColor))
            if (outside > 0f) Box(Modifier.weight(outside).fillMaxSize().background(outsideColor))
        }
    }
}

@Composable
private fun LegendStat(label: String, minutes: Long, total: Long, color: Color, alignEnd: Boolean = false) {
    val pct = if (total == 0L) 0 else (minutes * 100 / total).toInt()
    Column(horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            "${formatDuration(minutes)}  ·  $pct%",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun BucketRow(b: Bucket, maxMinutes: Long, insideColor: Color, outsideColor: Color) {
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(b.label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
            Text(
                formatMvr(b.overtimePay),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        Spacer(Modifier.height(8.dp))
        SplitBar(
            b.split, insideColor, outsideColor,
            height = 6.dp,
            widthFraction = (b.split.total.toFloat() / maxMinutes).coerceIn(0.02f, 1f),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "${formatDuration(b.split.insideMinutes)} in  ·  ${formatDuration(b.split.outsideMinutes)} out",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val dayLabel: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())
private val monthLabel: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM", Locale.getDefault())

private fun buckets(entries: List<TimeEntry>, settings: Settings, holidays: Set<LocalDate>, period: Period): List<Bucket> {
    val keyOf: (TimeEntry) -> Comparable<*> = when (period) {
        Period.MONTH -> { e -> e.clockIn.toLocalDate() }
        Period.YEAR -> { e -> YearMonth.from(e.clockIn.toLocalDate()) }
    }
    return entries
        .groupBy(keyOf)
        .toSortedMap(compareBy { it.toString() })
        .map { (key, group) ->
            val split = group.fold(Pay.Split(0, 0)) { acc, e -> acc + Pay.split(e, settings) }
            val pay = Pay.summarize(group, settings, holidays).overtimePay
            val label = when (key) {
                is LocalDate -> key.format(dayLabel)
                is YearMonth -> key.format(monthLabel)
                else -> key.toString()
            }
            Bucket(label, split, pay)
        }
}
