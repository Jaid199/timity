package com.jaid.timity.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class PayTest {
    private val zone: ZoneId = ZoneId.systemDefault()
    private fun at(date: LocalDate, h: Int, m: Int = 0): Long =
        LocalDateTime.of(date, java.time.LocalTime.of(h, m)).atZone(zone).toInstant().toEpochMilli()

    private val settings = Settings(
        overtimeRate = 50.0, standardHours = 8.0, workStart = 9 * 60, workEnd = 17 * 60,
        overtimeMode = OvertimeMode.BEYOND_STANDARD_HOURS,
    )
    private val windowed = settings.copy(overtimeMode = OvertimeMode.OUTSIDE_WORKING_HOURS)
    private val monday = LocalDate.of(2026, 11, 2)
    private val friday = LocalDate.of(2026, 11, 6)

    @Test
    fun `normal day overtime is time past the standard`() {
        val e = TimeEntry(clockIn = at(monday, 9), clockOut = at(monday, 19, 30))
        assertEquals(150, Pay.overtimeMinutes(e, Rules(settings)))
    }

    @Test
    fun `outside-hours mode counts from the moment the window ends`() {
        val rules = Rules(windowed)
        // 09:00-17:30: 30 minutes after the window.
        assertEquals(30, Pay.overtimeMinutes(TimeEntry(clockIn = at(monday, 9), clockOut = at(monday, 17, 30)), rules))
        // 10:00-16:00: inside the window, no overtime even though it is under 8 hours.
        assertEquals(0, Pay.overtimeMinutes(TimeEntry(clockIn = at(monday, 10), clockOut = at(monday, 16)), rules))
        // 08:00-18:00: an hour early and an hour late.
        assertEquals(120, Pay.overtimeMinutes(TimeEntry(clockIn = at(monday, 8), clockOut = at(monday, 18)), rules))
    }

    @Test
    fun `holiday makes every minute overtime`() {
        val e = TimeEntry(clockIn = at(monday, 9), clockOut = at(monday, 12))
        val rules = Rules(settings, holidays = setOf(monday))
        assertEquals(180, Pay.overtimeMinutes(e, rules))
        assertEquals(150.0, Pay.summarize(listOf(e), rules).overtimePay, 0.001)
    }

    @Test
    fun `weekly day off counts as overtime by default`() {
        val e = TimeEntry(clockIn = at(friday, 10), clockOut = at(friday, 14))
        assertEquals(240, Pay.overtimeMinutes(e, Rules(settings)))
        val weekdaysOnly = settings.copy(weekendDays = setOf(DayOfWeek.SUNDAY))
        assertEquals(0, Pay.overtimeMinutes(e, Rules(weekdaysOnly)))
    }

    @Test
    fun `ramadan period overrides standard hours and window`() {
        val ramadan = WorkPeriod(
            id = 1, name = "Ramadan",
            startEpochDay = monday.toEpochDay(), endEpochDay = monday.plusDays(29).toEpochDay(),
            workStart = 9 * 60, workEnd = 13 * 60, standardHours = 4.0,
        )
        val rules = Rules(settings, periods = listOf(ramadan))
        val e = TimeEntry(clockIn = at(monday, 9), clockOut = at(monday, 15))
        assertEquals(120, Pay.overtimeMinutes(e, rules))
        assertEquals(Pay.Split(insideMinutes = 240, outsideMinutes = 120), Pay.split(e, rules))

        val afterRamadan = monday.plusDays(30)
        val e2 = TimeEntry(clockIn = at(afterRamadan, 9), clockOut = at(afterRamadan, 15))
        assertEquals(0, Pay.overtimeMinutes(e2, rules))
        assertEquals(Pay.Split(insideMinutes = 360, outsideMinutes = 0), Pay.split(e2, rules))
    }

    @Test
    fun `a switched-off period is ignored`() {
        val ramadan = WorkPeriod(
            id = 1, name = "Ramadan",
            startEpochDay = monday.toEpochDay(), endEpochDay = monday.plusDays(29).toEpochDay(),
            workStart = 9 * 60, workEnd = 13 * 60, standardHours = 4.0, enabled = false,
        )
        val e = TimeEntry(clockIn = at(monday, 9), clockOut = at(monday, 15))
        assertEquals(0, Pay.overtimeMinutes(e, Rules(settings, periods = listOf(ramadan))))
    }

    @Test
    fun `split handles a session that spans midnight`() {
        val e = TimeEntry(clockIn = at(monday, 16), clockOut = at(monday.plusDays(1), 10))
        // 16:00-17:00 inside on day one, 09:00-10:00 inside on day two, 16 hours outside.
        assertEquals(Pay.Split(insideMinutes = 120, outsideMinutes = 16 * 60), Pay.split(e, Rules(settings)))
    }

    @Test
    fun `window wrapping midnight is honoured`() {
        val night = settings.copy(workStart = 22 * 60, workEnd = 6 * 60)
        val e = TimeEntry(clockIn = at(monday, 21), clockOut = at(monday.plusDays(1), 7))
        assertEquals(Pay.Split(insideMinutes = 8 * 60, outsideMinutes = 2 * 60), Pay.split(e, Rules(night)))
    }
}
