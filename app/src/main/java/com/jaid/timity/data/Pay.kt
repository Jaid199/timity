package com.jaid.timity.data

import kotlin.math.max

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

    /** Minutes beyond the daily standard for a single session. */
    fun overtimeMinutes(entry: TimeEntry, settings: Settings, now: Long = System.currentTimeMillis()): Long {
        val standard = (settings.standardHours * 60).toLong()
        return max(0L, workedMinutes(entry, now) - standard)
    }

    fun summarize(entries: List<TimeEntry>, settings: Settings, now: Long = System.currentTimeMillis()): Summary {
        var worked = 0L
        var overtime = 0L
        for (e in entries) {
            worked += workedMinutes(e, now)
            overtime += overtimeMinutes(e, settings, now)
        }
        return Summary(worked, overtime, settings.overtimeRate)
    }
}
