package com.jaid.timity.data.cloud

import com.jaid.timity.data.Holiday
import com.jaid.timity.data.OvertimeMode
import com.jaid.timity.data.Settings
import com.jaid.timity.data.ThemeMode
import com.jaid.timity.data.TimeEntry
import com.jaid.timity.data.WorkPeriod
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek

/** Mapping between Room rows and the JSON PostgREST speaks. Column names match supabase/migrations. */
internal object CloudJson {
    private fun JSONObject.longOrNull(key: String): Long? = if (isNull(key)) null else getLong(key)
    private fun JSONObject.putNullable(key: String, value: Any?): JSONObject = put(key, value ?: JSONObject.NULL)

    fun entry(e: TimeEntry): JSONObject = JSONObject()
        .put("id", e.uid)
        .put("clock_in", e.clockIn)
        .putNullable("clock_out", e.clockOut)
        .put("note", e.note.take(500))
        .put("updated_at", e.updatedAt)
        .putNullable("deleted_at", e.deletedAt)

    fun entry(o: JSONObject): TimeEntry = TimeEntry(
        uid = o.getString("id"),
        clockIn = o.getLong("clock_in"),
        clockOut = o.longOrNull("clock_out"),
        note = o.optString("note", ""),
        updatedAt = o.getLong("updated_at"),
        deletedAt = o.longOrNull("deleted_at"),
        dirty = false,
    )

    fun holiday(h: Holiday): JSONObject = JSONObject()
        .put("id", h.uid)
        .put("epoch_day", h.epochDay)
        .put("name", h.name.take(120))
        .put("type", h.type.take(60))
        .put("source", h.source)
        .put("enabled", h.enabled)
        .put("updated_at", h.updatedAt)
        .putNullable("deleted_at", h.deletedAt)

    fun holiday(o: JSONObject): Holiday = Holiday(
        uid = o.getString("id"),
        epochDay = o.getLong("epoch_day"),
        name = o.getString("name"),
        type = o.getString("type"),
        source = o.getString("source"),
        enabled = o.getBoolean("enabled"),
        updatedAt = o.getLong("updated_at"),
        deletedAt = o.longOrNull("deleted_at"),
        dirty = false,
    )

    fun period(p: WorkPeriod): JSONObject = JSONObject()
        .put("id", p.uid)
        .put("name", p.name.take(60))
        .put("start_epoch_day", p.startEpochDay)
        .put("end_epoch_day", p.endEpochDay)
        .put("work_start", p.workStart)
        .put("work_end", p.workEnd)
        .put("standard_hours", p.standardHours)
        .put("enabled", p.enabled)
        .put("updated_at", p.updatedAt)
        .putNullable("deleted_at", p.deletedAt)

    fun period(o: JSONObject): WorkPeriod = WorkPeriod(
        uid = o.getString("id"),
        name = o.getString("name"),
        startEpochDay = o.getLong("start_epoch_day"),
        endEpochDay = o.getLong("end_epoch_day"),
        workStart = o.getInt("work_start"),
        workEnd = o.getInt("work_end"),
        standardHours = o.getDouble("standard_hours"),
        enabled = o.getBoolean("enabled"),
        updatedAt = o.getLong("updated_at"),
        deletedAt = o.longOrNull("deleted_at"),
        dirty = false,
    )

    fun settings(s: Settings, userId: String, updatedAt: Long): JSONObject = JSONObject()
        .put("user_id", userId)
        .put("overtime_rate", s.overtimeRate)
        .put("standard_hours", s.standardHours)
        .put("overtime_mode", s.overtimeMode.name)
        .put("work_start", s.workStart)
        .put("work_end", s.workEnd)
        .put("weekend_days", JSONArray(s.weekendDays.map { it.name }))
        .put("theme", s.theme.name)
        .put("holidays_synced_at", s.holidaysSyncedAt)
        .put("updated_at", updatedAt)

    fun settings(o: JSONObject): Settings {
        val days = o.optJSONArray("weekend_days")
        return Settings(
            overtimeRate = o.getDouble("overtime_rate"),
            standardHours = o.getDouble("standard_hours"),
            overtimeMode = runCatching { OvertimeMode.valueOf(o.getString("overtime_mode")) }.getOrDefault(OvertimeMode.OUTSIDE_WORKING_HOURS),
            theme = runCatching { ThemeMode.valueOf(o.getString("theme")) }.getOrDefault(ThemeMode.SYSTEM),
            workStart = o.getInt("work_start"),
            workEnd = o.getInt("work_end"),
            holidaysSyncedAt = o.optLong("holidays_synced_at", 0L),
            weekendDays = if (days == null) Settings.DEFAULT_WEEKEND else
                (0 until days.length()).mapNotNull { i -> runCatching { DayOfWeek.valueOf(days.getString(i)) }.getOrNull() }.toSet(),
        )
    }
}
