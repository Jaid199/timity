package com.jaid.timity.ui

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val zone: ZoneId get() = ZoneId.systemDefault()

fun Long.toLocalDateTime(): LocalDateTime = Instant.ofEpochMilli(this).atZone(zone).toLocalDateTime()
fun LocalDateTime.toEpochMillis(): Long = atZone(zone).toInstant().toEpochMilli()
fun Long.toLocalDate(): LocalDate = toLocalDateTime().toLocalDate()

private val timeFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val dayFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, d MMM")
private val fullDayFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, d MMM yyyy")

fun Long.formatTime(): String = toLocalDateTime().format(timeFmt)
fun LocalTime.formatTime(): String = format(timeFmt)
fun LocalDate.formatDay(withYear: Boolean = false): String = format(if (withYear) fullDayFmt else dayFmt)

/** 125 minutes -> "2h 05m". */
fun formatDuration(minutes: Long): String {
    val h = minutes / 60
    val m = minutes % 60
    return if (h == 0L) "${m}m" else "${h}h %02dm".format(m)
}

fun formatHours(hours: Double): String = String.format(Locale.US, "%.2f h", hours)

fun formatMvr(amount: Double): String = "MVR " + String.format(Locale.US, "%,.2f", amount)
