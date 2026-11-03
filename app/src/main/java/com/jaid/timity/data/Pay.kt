package com.jaid.timity.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.max
import kotlin.math.min

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

    fun workedMinutes(entry: TimeEntry, now: Long = System.currentTimeMillis()): Long {
        val end = entry.clockOut ?: now
        return max(0L, (end - entry.clockIn) / 60_000)
    }

    /** True on a public/custom holiday or a weekly day off. */
    fun isDayOff(date: LocalDate, settings: Settings, holidays: Set<LocalDate>): Boolean =
        date.dayOfWeek in settings.weekendDays || date in holidays

    fun isHoliday(entry: TimeEntry, settings: Settings, holidays: Set<LocalDate>, zone: ZoneId = ZoneId.systemDefault()): Boolean =
        isDayOff(Instant.ofEpochMilli(entry.clockIn).atZone(zone).toLocalDate(), settings, holidays)

    /** Minutes beyond the daily standard for a single session. On a holiday every minute is overtime. */
    fun overtimeMinutes(
        entry: TimeEntry,
        settings: Settings,
        holidays: Set<LocalDate> = emptySet(),
        now: Long = System.currentTimeMillis(),
    ): Long {
        val worked = workedMinutes(entry, now)
        if (isHoliday(entry, settings, holidays)) return worked
        val standard = (settings.standardHours * 60).toLong()
        return max(0L, worked - standard)
    }

    /** Minutes worked inside and outside the configured working-hours window. */
    data class Split(val insideMinutes: Long, val outsideMinutes: Long) {
        val total: Long get() = insideMinutes + outsideMinutes
        operator fun plus(o: Split) = Split(insideMinutes + o.insideMinutes, outsideMinutes + o.outsideMinutes)
    }

    fun split(
        entry: TimeEntry,
        settings: Settings,
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
            for ((wStart, wEnd) in windowsOn(day, settings, zone)) {
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
    private fun windowsOn(day: LocalDate, settings: Settings, zone: ZoneId): List<Pair<Long, Long>> {
        val midnight = day.atStartOfDay(zone)
        fun at(minutes: Int) = midnight.plusMinutes(minutes.toLong()).toInstant().toEpochMilli()
        val nextMidnight = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return when {
            settings.workStart == settings.workEnd -> emptyList()
            settings.workStart < settings.workEnd -> listOf(at(settings.workStart) to at(settings.workEnd))
            else -> listOf(midnight.toInstant().toEpochMilli() to at(settings.workEnd), at(settings.workStart) to nextMidnight)
        }
    }

    fun summarize(
        entries: List<TimeEntry>,
        settings: Settings,
        holidays: Set<LocalDate> = emptySet(),
        now: Long = System.currentTimeMillis(),
    ): Summary {
        var worked = 0L
        var overtime = 0L
        for (e in entries) {
            worked += workedMinutes(e, now)
            overtime += overtimeMinutes(e, settings, holidays, now)
        }
        return Summary(worked, overtime, settings.overtimeRate)
    }
}
