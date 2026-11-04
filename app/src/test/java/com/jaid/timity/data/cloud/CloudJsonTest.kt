package com.jaid.timity.data.cloud

import com.jaid.timity.data.Holiday
import com.jaid.timity.data.OvertimeMode
import com.jaid.timity.data.Settings
import com.jaid.timity.data.ThemeMode
import com.jaid.timity.data.TimeEntry
import com.jaid.timity.data.WorkPeriod
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek

class CloudJsonTest {
    @Test
    fun `entry round-trips, including an open session`() {
        val open = TimeEntry(id = 7, clockIn = 1_762_152_000_000, note = "Month-end", updatedAt = 5, dirty = true)
        val json = CloudJson.entry(open)
        assertTrue(json.isNull("clock_out"))
        assertEquals(open.uid, json.getString("id"))
        val back = CloudJson.entry(JSONObject(json.toString()))
        assertEquals(open.copy(id = 0, dirty = false), back)
        assertFalse(back.dirty)
    }

    @Test
    fun `holiday uid is derived from day and name`() {
        val a = Holiday(epochDay = 20760, name = "Victory Day", type = "National holiday", source = Holiday.SOURCE_API, enabled = true)
        val b = Holiday(epochDay = 20760, name = " Victory Day ", type = "Custom", source = Holiday.SOURCE_CUSTOM, enabled = false)
        assertEquals(a.uid, b.uid)
        val back = CloudJson.holiday(CloudJson.holiday(a.copy(updatedAt = 9)))
        assertEquals(a.copy(updatedAt = 9, dirty = false), back)
    }

    @Test
    fun `period round-trips`() {
        val p = WorkPeriod(name = "Ramadan 2026", startEpochDay = 20500, endEpochDay = 20529, workStart = 540, workEnd = 780, standardHours = 4.0, updatedAt = 3)
        assertEquals(p.copy(dirty = false), CloudJson.period(CloudJson.period(p)))
    }

    @Test
    fun `settings round-trip and tolerate unknown enum values`() {
        val s = Settings(
            overtimeRate = 75.0, standardHours = 7.5, overtimeMode = OvertimeMode.BEYOND_STANDARD_HOURS, theme = ThemeMode.DARK,
            workStart = 540, workEnd = 960, holidayApiKey = "secret", holidaysSyncedAt = 123, weekendDays = setOf(DayOfWeek.SUNDAY),
        )
        val json = CloudJson.settings(s, "user-1", 42)
        assertFalse("the API key must stay on the phone", json.toString().contains("secret"))
        assertEquals(42, json.getLong("updated_at"))
        assertEquals(s.copy(holidayApiKey = ""), CloudJson.settings(json))

        json.put("theme", "NEON").put("overtime_mode", "???")
        val lenient = CloudJson.settings(json)
        assertEquals(ThemeMode.SYSTEM, lenient.theme)
        assertEquals(OvertimeMode.OUTSIDE_WORKING_HOURS, lenient.overtimeMode)
    }
}
