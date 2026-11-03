package com.jaid.timity.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

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
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("settings")

class SettingsRepository(context: Context) {
    private val store = context.applicationContext.dataStore

    private object Keys {
        val overtimeRate = doublePreferencesKey("overtime_rate")
        val standardHours = doublePreferencesKey("standard_hours")
        val theme = stringPreferencesKey("theme")
        val workStart = intPreferencesKey("work_start")
        val workEnd = intPreferencesKey("work_end")
    }

    val settings: Flow<Settings> = store.data.map { p ->
        Settings(
            overtimeRate = p[Keys.overtimeRate] ?: 0.0,
            standardHours = p[Keys.standardHours] ?: 8.0,
            theme = p[Keys.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.SYSTEM,
            workStart = p[Keys.workStart] ?: 9 * 60,
            workEnd = p[Keys.workEnd] ?: 17 * 60,
        )
    }

    suspend fun setOvertimeRate(rate: Double) = store.edit { it[Keys.overtimeRate] = rate }
    suspend fun setStandardHours(hours: Double) = store.edit { it[Keys.standardHours] = hours }
    suspend fun setTheme(mode: ThemeMode) = store.edit { it[Keys.theme] = mode.name }
    suspend fun setWorkWindow(startMinutes: Int, endMinutes: Int) = store.edit {
        it[Keys.workStart] = startMinutes
        it[Keys.workEnd] = endMinutes
    }
}
