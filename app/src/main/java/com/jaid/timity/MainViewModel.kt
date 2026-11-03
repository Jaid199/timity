package com.jaid.timity

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jaid.timity.data.AppDatabase
import com.jaid.timity.data.Settings
import com.jaid.timity.data.SettingsRepository
import com.jaid.timity.data.ThemeMode
import com.jaid.timity.data.TimeEntry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

enum class Period { MONTH, YEAR }

/** Which slice of history is being viewed. [month] is ignored when [period] is YEAR. */
data class HistoryFilter(
    val period: Period = Period.MONTH,
    val year: Int = LocalDate.now().year,
    val month: Int = LocalDate.now().monthValue,
) {
    fun range(zone: ZoneId = ZoneId.systemDefault()): LongRange {
        val start = when (period) {
            Period.MONTH -> YearMonth.of(year, month).atDay(1)
            Period.YEAR -> LocalDate.of(year, 1, 1)
        }
        val end = when (period) {
            Period.MONTH -> YearMonth.of(year, month).plusMonths(1).atDay(1)
            Period.YEAR -> LocalDate.of(year + 1, 1, 1)
        }
        return start.atStartOfDay(zone).toInstant().toEpochMilli() until
            end.atStartOfDay(zone).toInstant().toEpochMilli()
    }

    fun previous(): HistoryFilter = when (period) {
        Period.MONTH -> YearMonth.of(year, month).minusMonths(1).let { copy(year = it.year, month = it.monthValue) }
        Period.YEAR -> copy(year = year - 1)
    }

    fun next(): HistoryFilter = when (period) {
        Period.MONTH -> YearMonth.of(year, month).plusMonths(1).let { copy(year = it.year, month = it.monthValue) }
        Period.YEAR -> copy(year = year + 1)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.get(app).timeEntryDao()
    private val settingsRepo = SettingsRepository(app)

    val settings: StateFlow<Settings> = settingsRepo.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Settings())

    val openEntry: StateFlow<TimeEntry?> = dao.openEntry()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val todayEntries: StateFlow<List<TimeEntry>> = run {
        val zone = ZoneId.systemDefault()
        val start = LocalDate.now().atStartOfDay(zone).toInstant().toEpochMilli()
        val end = LocalDate.now().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        dao.between(start, end).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    }

    private val _filter = MutableStateFlow(HistoryFilter())
    val filter: StateFlow<HistoryFilter> = _filter

    val historyEntries: StateFlow<List<TimeEntry>> = _filter
        .flatMapLatest { f -> f.range().let { dao.between(it.first, it.last + 1) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setFilter(f: HistoryFilter) { _filter.value = f }

    fun clockIn() = viewModelScope.launch {
        if (openEntry.value == null) dao.insert(TimeEntry(clockIn = System.currentTimeMillis()))
    }

    fun clockOut() = viewModelScope.launch {
        openEntry.value?.let { dao.update(it.copy(clockOut = System.currentTimeMillis())) }
    }

    fun save(entry: TimeEntry) = viewModelScope.launch {
        if (entry.id == 0L) dao.insert(entry) else dao.update(entry)
    }

    fun delete(entry: TimeEntry) = viewModelScope.launch { dao.delete(entry) }

    fun setOvertimeRate(rate: Double) = viewModelScope.launch { settingsRepo.setOvertimeRate(rate) }
    fun setStandardHours(hours: Double) = viewModelScope.launch { settingsRepo.setStandardHours(hours) }
    fun setTheme(mode: ThemeMode) = viewModelScope.launch { settingsRepo.setTheme(mode) }
}
