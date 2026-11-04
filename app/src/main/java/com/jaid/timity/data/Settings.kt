package com.jaid.timity.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** How overtime is measured on a normal working day. Holidays and days off are always fully overtime. */
enum class OvertimeMode {
    /** Any minute before the working-hours window starts or after it ends. Clocking out after 16:00 counts at once. */
    OUTSIDE_WORKING_HOURS,
    /** Minutes worked beyond the standard hours per day, whatever the clock times. */
    BEYOND_STANDARD_HOURS,
}

data class Settings(
    /** Overtime pay per hour, in MVR. */
    val overtimeRate: Double = 0.0,
    /** Hours per day before overtime starts; used by [OvertimeMode.BEYOND_STANDARD_HOURS]. */
    val standardHours: Double = 8.0,
    val overtimeMode: OvertimeMode = OvertimeMode.OUTSIDE_WORKING_HOURS,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    /** Working-hours window, as minutes from midnight. May wrap past midnight when start > end. */
    val workStart: Int = 9 * 60,
    val workEnd: Int = 17 * 60,
    /** Calendarific API key; blank until the user enters one. */
    val holidayApiKey: String = "",
    /** Epoch millis of the last successful holiday sync, or 0. */
    val holidaysSyncedAt: Long = 0L,
    /** Weekly days off; all time worked on them is overtime. Maldives weekend by default. */
    val weekendDays: Set<DayOfWeek> = DEFAULT_WEEKEND,
) {
    companion object {
        val DEFAULT_WEEKEND: Set<DayOfWeek> = setOf(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY)
    }
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("settings")

class SettingsRepository(context: Context) {
    private val store = context.applicationContext.dataStore

    private object Keys {
        val overtimeRate = doublePreferencesKey("overtime_rate")
        val standardHours = doublePreferencesKey("standard_hours")
        val theme = stringPreferencesKey("theme")
        val overtimeMode = stringPreferencesKey("overtime_mode")
        val workStart = intPreferencesKey("work_start")
        val workEnd = intPreferencesKey("work_end")
        val holidayApiKey = stringPreferencesKey("holiday_api_key")
        val holidaysSyncedAt = longPreferencesKey("holidays_synced_at")
        val weekendDays = stringSetPreferencesKey("weekend_days")
        /** Cloud sync bookkeeping: when the synced fields last changed here, and whether that change is still unpushed. */
        val updatedAt = longPreferencesKey("updated_at")
        val dirty = booleanPreferencesKey("dirty")
    }

    val settings: Flow<Settings> = store.data.map { p ->
        Settings(
            overtimeRate = p[Keys.overtimeRate] ?: 0.0,
            standardHours = p[Keys.standardHours] ?: 8.0,
            theme = p[Keys.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.SYSTEM,
            overtimeMode = p[Keys.overtimeMode]?.let { runCatching { OvertimeMode.valueOf(it) }.getOrNull() }
                ?: OvertimeMode.OUTSIDE_WORKING_HOURS,
            workStart = p[Keys.workStart] ?: 9 * 60,
            workEnd = p[Keys.workEnd] ?: 17 * 60,
            holidayApiKey = p[Keys.holidayApiKey] ?: "",
            holidaysSyncedAt = p[Keys.holidaysSyncedAt] ?: 0L,
            weekendDays = p[Keys.weekendDays]
                ?.mapNotNull { runCatching { DayOfWeek.valueOf(it) }.getOrNull() }?.toSet()
                ?: Settings.DEFAULT_WEEKEND,
        )
    }

    private fun Settings.write(p: MutablePreferences) {
        p[Keys.overtimeRate] = overtimeRate
        p[Keys.standardHours] = standardHours
        p[Keys.theme] = theme.name
        p[Keys.overtimeMode] = overtimeMode.name
        p[Keys.workStart] = workStart
        p[Keys.workEnd] = workEnd
        p[Keys.holidaysSyncedAt] = holidaysSyncedAt
        p[Keys.weekendDays] = weekendDays.map { d -> d.name }.toSet()
    }

    /** A local change to a synced field: stamp it and flag it for the next push. */
    private suspend fun change(block: (MutablePreferences) -> Unit) {
        store.edit {
            block(it)
            it[Keys.updatedAt] = System.currentTimeMillis()
            it[Keys.dirty] = true
        }
        onChanged?.invoke()
    }

    /** Called after every synced change; the repository hooks the cloud sync here. */
    var onChanged: (() -> Unit)? = null

    suspend fun setOvertimeRate(rate: Double) = change { it[Keys.overtimeRate] = rate }
    suspend fun setStandardHours(hours: Double) = change { it[Keys.standardHours] = hours }
    suspend fun setTheme(mode: ThemeMode) = change { it[Keys.theme] = mode.name }
    suspend fun setOvertimeMode(mode: OvertimeMode) = change { it[Keys.overtimeMode] = mode.name }
    /** Phone-only: the API key never leaves the device, so it is not stamped. */
    suspend fun setHolidayApiKey(key: String) = store.edit { it[Keys.holidayApiKey] = key.trim() }
    suspend fun setWeekendDays(days: Set<DayOfWeek>) = change { it[Keys.weekendDays] = days.map { d -> d.name }.toSet() }
    suspend fun setHolidaysSyncedAt(millis: Long) = change { it[Keys.holidaysSyncedAt] = millis }
    suspend fun setWorkWindow(startMinutes: Int, endMinutes: Int) = change {
        it[Keys.workStart] = startMinutes
        it[Keys.workEnd] = endMinutes
    }

    class SyncSnapshot(val settings: Settings, val updatedAt: Long, val dirty: Boolean)

    suspend fun snapshot(): SyncSnapshot {
        val p = store.data.first()
        return SyncSnapshot(settings.first(), p[Keys.updatedAt] ?: 0L, p[Keys.dirty] ?: false)
    }

    /** Clears the dirty flag unless another change landed while the push was in flight. */
    suspend fun markPushed(updatedAt: Long) = store.edit {
        if ((it[Keys.updatedAt] ?: 0L) == updatedAt) it[Keys.dirty] = false
    }

    /** Overwrites the synced fields with a newer copy from the cloud. The API key is kept. */
    suspend fun applyRemote(remote: Settings, updatedAt: Long) = store.edit {
        remote.write(it)
        it[Keys.updatedAt] = updatedAt
        it[Keys.dirty] = false
    }
}
