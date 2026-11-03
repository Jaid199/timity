package com.jaid.timity.data

import java.time.LocalDate

/** Derives Ramadan's date range from synced holidays so a new work period can be pre-filled. */
object Ramadan {
    data class Range(val start: LocalDate, val end: LocalDate)

    /**
     * Finds the Ramadan that is current or next relative to [today]. The start comes from a holiday
     * named like "Ramadan Start"; the end is the day before the Eid al-Fitr entry that follows it,
     * or 29 days after the start when no Eid entry is synced. Returns null when nothing is synced.
     */
    fun upcoming(holidays: List<Holiday>, today: LocalDate = LocalDate.now()): Range? {
        val starts = holidays
            .filter { it.name.contains("ramadan", ignoreCase = true) && it.name.contains("start", ignoreCase = true) }
            .map { it.date }
            .sorted()
        if (starts.isEmpty()) return null

        val eids = holidays
            .filter { it.name.contains("fitr", ignoreCase = true) }
            .map { it.date }
            .sorted()

        val ranges = starts.map { start ->
            val eid = eids.firstOrNull { it.isAfter(start) && !it.isAfter(start.plusDays(40)) }
            Range(start, eid?.minusDays(1) ?: start.plusDays(29))
        }
        return ranges.firstOrNull { !it.end.isBefore(today) } ?: ranges.last()
    }
}
