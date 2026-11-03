package com.jaid.timity.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.max
import kotlin.math.min

/** Everything needed to judge a day: base settings, holidays, and special periods like Ramadan. */
data class Rules(
    val settings: Settings = Settings(),
    val holidays: Set<LocalDate> = emptySet(),
    val periods: List<WorkPeriod> = emptyList(),
) {
    fun periodFor(date: LocalDate): WorkPeriod? = periods.firstOrNull { it.enabled && it.contains(date) }

    fun standardHours(date: LocalDate): Double = periodFor(date)?.standardHours ?: settings.standardHours

    /** Working-hours window for the day as minutes from midnight (start, end). */
    fun window(date: LocalDate): Pair<Int, Int> =
        periodFor(date)?.let { it.workStart to it.workEnd } ?: (settings.workStart to settings.workEnd)

    /** True on a public/custom holiday or a weekly day off. */
    fun isDayOff(date: LocalDate): Boolean = date.dayOfWeek in settings.weekendDays || date in holidays
}

/** Pure overtime arithmetic, kept separate from UI and storage. */
object Pay {
    data class Summary(
        val workedMinutes: Long,
        val overtimeMinutes: Long,
        val overtimeRate: Double,
    ) {
        val workedHours: Double get() = workedMinutes / 60.0
        val overtimeHours: Double get() = overtimeMinutes / 60.0
        val overtimePay: Double get() = overtimeHours * overtimeRate
    }

    /** Minutes worked inside and outside the working-hours window. */
    data class Split(val insideMinutes: Long, val outsideMinutes: Long) {
        val total: Long get() = insideMinutes + outsideMinutes
        operator fun plus(o: Split) = Split(insideMinutes + o.insideMinutes, outsideMinutes + o.outsideMinutes)
    }

    fun dateOf(entry: TimeEntry, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
        Instant.ofEpochMilli(entry.clockIn).atZone(zone).toLocalDate()

    fun workedMinutes(entry: TimeEntry, now: Long = System.currentTimeMillis()): Long {
        val end = entry.clockOut ?: now
        return max(0L, (end - entry.clockIn) / 60_000)
    }

    fun isHoliday(entry: TimeEntry, rules: Rules): Boolean = rules.isDayOff(dateOf(entry))

    /**
     * Overtime for a single session. On a holiday or day off every minute is overtime; otherwise it depends on
     * [Settings.overtimeMode]: time outside the working-hours window, or time beyond the standard day.
     */
    fun overtimeMinutes(entry: TimeEntry, rules: Rules, now: Long = System.currentTimeMillis()): Long {
        val worked = workedMinutes(entry, now)
        val date = dateOf(entry)
        if (rules.isDayOff(date)) return worked
        return when (rules.settings.overtimeMode) {
            OvertimeMode.OUTSIDE_WORKING_HOURS -> split(entry, rules, now).outsideMinutes
            OvertimeMode.BEYOND_STANDARD_HOURS -> {
                val standard = (rules.standardHours(date) * 60).toLong()
                max(0L, worked - standard)
            }
        }
    }

    fun summarize(entries: List<TimeEntry>, rules: Rules, now: Long = System.currentTimeMillis()): Summary {
        var worked = 0L
        var overtime = 0L
        for (e in entries) {
            worked += workedMinutes(e, now)
            overtime += overtimeMinutes(e, rules, now)
        }
        return Summary(worked, overtime, rules.settings.overtimeRate)
    }

    fun split(
        entry: TimeEntry,
        rules: Rules,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): Split {
        val start = entry.clockIn
        val end = entry.clockOut ?: now
        if (end <= start) return Split(0, 0)

        var inside = 0L
        var day = Instant.ofEpochMilli(start).atZone(zone).toLocalDate()
        val lastDay = Instant.ofEpochMilli(end).atZone(zone).toLocalDate()
        while (!day.isAfter(lastDay)) {
            for ((wStart, wEnd) in windowsOn(day, rules, zone)) {
                val overlap = min(end, wEnd) - max(start, wStart)
                if (overlap > 0) inside += overlap
            }
            day = day.plusDays(1)
        }
        val insideMin = inside / 60_000
        val totalMin = (end - start) / 60_000
        return Split(insideMin, max(0L, totalMin - insideMin))
    }

    /** The working-hours window(s) on one calendar day, as epoch-millis ranges. Two when it wraps midnight. */
    private fun windowsOn(day: LocalDate, rules: Rules, zone: ZoneId): List<Pair<Long, Long>> {
        val (startMin, endMin) = rules.window(day)
        val midnight = day.atStartOfDay(zone)
        fun at(minutes: Int) = midnight.plusMinutes(minutes.toLong()).toInstant().toEpochMilli()
        val nextMidnight = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return when {
            startMin == endMin -> emptyList()
            startMin < endMin -> listOf(at(startMin) to at(endMin))
            else -> listOf(midnight.toInstant().toEpochMilli() to at(endMin), at(startMin) to nextMidnight)
        }
    }
}
