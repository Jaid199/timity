package com.jaid.timity.ui

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The locale the app is currently displayed in; set by MainActivity after AppCompat applies the per-app language. */
object AppLocale {
    @Volatile var current: Locale = Locale.getDefault()
}

private val zone: ZoneId get() = ZoneId.systemDefault()

fun Long.toLocalDateTime(): LocalDateTime = Instant.ofEpochMilli(this).atZone(zone).toLocalDateTime()
fun LocalDateTime.toEpochMillis(): Long = atZone(zone).toInstant().toEpochMilli()
fun Long.toLocalDate(): LocalDate = toLocalDateTime().toLocalDate()

private fun fmt(pattern: String): DateTimeFormatter = DateTimeFormatter.ofPattern(pattern, AppLocale.current)

fun Long.formatTime(): String = toLocalDateTime().format(fmt("HH:mm"))
fun LocalTime.formatTime(): String = format(fmt("HH:mm"))
fun LocalDate.formatDay(withYear: Boolean = false): String = format(fmt(if (withYear) "EEE, d MMM yyyy" else "EEE, d MMM"))
fun LocalDate.formatShortDay(): String = format(fmt("EEE d MMM"))
fun LocalDate.formatDate(): String = format(fmt("d MMM yyyy"))

/** 125 minutes -> "2h 05m". Units stay Latin in every language; they read fine in Dhivehi too. */
fun formatDuration(minutes: Long): String {
    val h = minutes / 60
    val m = minutes % 60
    return if (h == 0L) "${m}m" else "${h}h %02dm".format(Locale.US, m)
}

fun formatHours(hours: Double): String = String.format(Locale.US, "%.2f h", hours)

fun formatMvr(amount: Double): String = "MVR " + String.format(Locale.US, "%,.2f", amount)
