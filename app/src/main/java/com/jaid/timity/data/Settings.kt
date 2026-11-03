package com.jaid.timity.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class Settings(
    /** Overtime pay per hour, in MVR. */
    val overtimeRate: Double = 0.0,
    /** Hours per day before overtime starts. */
    val standardHours: Double = 8.0,
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
        val workStart = intPreferencesKey("work_start")
        val workEnd = intPreferencesKey("work_end")
        val holidayApiKey = stringPreferencesKey("holiday_api_key")
        val holidaysSyncedAt = longPreferencesKey("holidays_synced_at")
        val weekendDays = stringSetPreferencesKey("weekend_days")
    }

    val settings: Flow<Settings> = store.data.map { p ->
        Settings(
            overtimeRate = p[Keys.overtimeRate] ?: 0.0,
            standardHours = p[Keys.standardHours] ?: 8.0,
            theme = p[Keys.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.SYSTEM,
            workStart = p[Keys.workStart] ?: 9 * 60,
            workEnd = p[Keys.workEnd] ?: 17 * 60,
            holidayApiKey = p[Keys.holidayApiKey] ?: "",
            holidaysSyncedAt = p[Keys.holidaysSyncedAt] ?: 0L,
            weekendDays = p[Keys.weekendDays]
                ?.mapNotNull { runCatching { DayOfWeek.valueOf(it) }.getOrNull() }?.toSet()
                ?: Settings.DEFAULT_WEEKEND,
        )
    }

    suspend fun setOvertimeRate(rate: Double) = store.edit { it[Keys.overtimeRate] = rate }
    suspend fun setStandardHours(hours: Double) = store.edit { it[Keys.standardHours] = hours }
    suspend fun setTheme(mode: ThemeMode) = store.edit { it[Keys.theme] = mode.name }
    suspend fun setHolidayApiKey(key: String) = store.edit { it[Keys.holidayApiKey] = key.trim() }
    suspend fun setWeekendDays(days: Set<DayOfWeek>) = store.edit { it[Keys.weekendDays] = days.map { d -> d.name }.toSet() }
    suspend fun setHolidaysSyncedAt(millis: Long) = store.edit { it[Keys.holidaysSyncedAt] = millis }
    suspend fun setWorkWindow(startMinutes: Int, endMinutes: Int) = store.edit {
        it[Keys.workStart] = startMinutes
        it[Keys.workEnd] = endMinutes
    }
}
