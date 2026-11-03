package com.jaid.timity.ui

import com.jaid.timity.R
import androidx.compose.ui.res.stringResource
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
import com.jaid.timity.data.Rules
import com.jaid.timity.data.TimeEntry
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

/** One row of the breakdown list: a day in month view, a month in year view. */
private data class Bucket(val label: String, val split: Pay.Split, val overtimePay: Double)

@Composable
fun ReportsScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    val filter by vm.filter.collectAsStateWithLifecycle()
    val entries by vm.historyEntries.collectAsStateWithLifecycle()
    val rules by vm.rules.collectAsStateWithLifecycle()
    val settings = rules.settings

    val summary = Pay.summarize(entries, rules)
    val split = entries.fold(Pay.Split(0, 0)) { acc, e -> acc + Pay.split(e, rules) }
    val daysWorked = entries.map { it.clockIn.toLocalDate() }.distinct().size
    val buckets = buckets(entries, rules, filter.period)
    val range = filter.range()
    val activePeriods = rules.periods.filter { p ->
        p.enabled && p.end.toEpochDay() >= range.first.toLocalDate().toEpochDay() && p.start.toEpochDay() <= range.last.toLocalDate().toEpochDay()
    }

    val insideColor = MaterialTheme.colorScheme.onBackground
    val outsideColor = MaterialTheme.colorScheme.outline

    LazyColumn(modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        item {
            Spacer(Modifier.height(16.dp))
            PeriodToggle(filter, onChange = vm::setFilter, modifier = Modifier.fillMaxWidth())
            PeriodSelector(filter, onChange = vm::setFilter)
            Spacer(Modifier.height(8.dp))

            // Hero number: overtime pay.
            Text(stringResource(R.string.overtime_pay), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                formatMvr(summary.overtimePay),
                fontSize = 40.sp,
                fontWeight = FontWeight.Light,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                stringResource(R.string.overtime_summary, formatHours(summary.overtimeHours), formatMvr(settings.overtimeRate)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Stat(stringResource(R.string.total_worked), formatDuration(summary.workedMinutes))
                Stat(stringResource(R.string.days), daysWorked.toString())
                Stat(
                    stringResource(R.string.avg_per_day),
                    if (daysWorked == 0) "–" else formatDuration(summary.workedMinutes / daysWorked),
                    alignEnd = true,
                )
            }
            Spacer(Modifier.height(28.dp))

            // Inside vs outside working hours.
            val window = "${LocalTime.ofSecondOfDay(settings.workStart * 60L).formatTime()} – " +
                LocalTime.ofSecondOfDay(settings.workEnd * 60L).formatTime()
            val periodNote = if (activePeriods.isEmpty()) "" else "  ·  " + stringResource(R.string.hours_applied, activePeriods.joinToString { it.name })
            Text(stringResource(R.string.working_hours_range, window) + periodNote, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            SplitBar(split, insideColor, outsideColor, height = 12.dp)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                LegendStat(stringResource(R.string.within_hours), split.insideMinutes, split.total, insideColor)
                LegendStat(stringResource(R.string.outside_hours), split.outsideMinutes, split.total, outsideColor, alignEnd = true)
            }
            Spacer(Modifier.height(28.dp))

            Text(
                stringResource(if (filter.period == Period.MONTH) R.string.by_day else R.string.by_month),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            if (buckets.isEmpty()) {
                Spacer(Modifier.height(32.dp))
                Text(
                    stringResource(R.string.no_entries),
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
            stringResource(R.string.in_out_line, formatDuration(b.split.insideMinutes), formatDuration(b.split.outsideMinutes)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}


private fun buckets(entries: List<TimeEntry>, rules: Rules, period: Period): List<Bucket> {
    val keyOf: (TimeEntry) -> Comparable<*> = when (period) {
        Period.MONTH -> { e -> e.clockIn.toLocalDate() }
        Period.YEAR -> { e -> YearMonth.from(e.clockIn.toLocalDate()) }
    }
    return entries
        .groupBy(keyOf)
        .toSortedMap(compareBy { it.toString() })
        .map { (key, group) ->
            val split = group.fold(Pay.Split(0, 0)) { acc, e -> acc + Pay.split(e, rules) }
            val pay = Pay.summarize(group, rules).overtimePay
            val label = when (key) {
                is LocalDate -> key.formatShortDay()
                is YearMonth -> key.month.getDisplayName(java.time.format.TextStyle.FULL, AppLocale.current)
                else -> key.toString()
            }
            Bucket(label, split, pay)
        }
}
