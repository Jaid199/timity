package com.jaid.timity

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jaid.timity.data.AppDatabase
import com.jaid.timity.data.Holiday
import com.jaid.timity.data.HolidayApi
import com.jaid.timity.data.OvertimeMode
import com.jaid.timity.data.Rules
import com.jaid.timity.data.WorkPeriod
import com.jaid.timity.data.Settings
import com.jaid.timity.data.SettingsRepository
import com.jaid.timity.data.ThemeMode
import com.jaid.timity.data.TimeEntry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
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
    private val holidayDao = AppDatabase.get(app).holidayDao()
    private val periodDao = AppDatabase.get(app).workPeriodDao()
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

    val holidays: StateFlow<List<Holiday>> = holidayDao.all()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Dates on which all worked time is overtime. */
    val holidayDates: StateFlow<Set<LocalDate>> = holidayDao.all()
        .map { list -> list.filter { it.enabled }.map { it.date }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val periods: StateFlow<List<WorkPeriod>> = periodDao.all()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Settings, holidays and special periods bundled for the pay calculator. */
    val rules: StateFlow<Rules> = combine(settings, holidayDates, periods) { s, h, p -> Rules(s, h, p) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Rules())

    fun savePeriod(p: WorkPeriod) = viewModelScope.launch { if (p.id == 0L) periodDao.insert(p) else periodDao.update(p) }
    fun deletePeriod(p: WorkPeriod) = viewModelScope.launch { periodDao.delete(p) }

    sealed interface SyncState {
        data object Idle : SyncState
        data object Running : SyncState
        data class Done(val added: Int, val total: Int) : SyncState
        data object MissingKey : SyncState
        data class Failed(val message: String) : SyncState
    }

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState

    /** Pull this year's and next year's Maldives holidays. Existing rows and their toggles are kept. */
    fun syncHolidays() = viewModelScope.launch {
        val key = settings.value.holidayApiKey
        if (key.isBlank()) {
            _syncState.value = SyncState.MissingKey
            return@launch
        }
        _syncState.value = SyncState.Running
        val year = LocalDate.now().year
        runCatching {
            val fetched = HolidayApi.fetch(key, "MV", year) + HolidayApi.fetch(key, "MV", year + 1)
            val ids = holidayDao.insertAll(fetched)
            settingsRepo.setHolidaysSyncedAt(System.currentTimeMillis())
            SyncState.Done(added = ids.count { it != -1L }, total = fetched.size)
        }.onSuccess { _syncState.value = it }
            .onFailure { _syncState.value = SyncState.Failed(it.message ?: it.javaClass.simpleName) }
    }

    fun addHoliday(date: LocalDate, name: String) = viewModelScope.launch {
        holidayDao.insert(
            Holiday(epochDay = date.toEpochDay(), name = name.trim(), type = "Custom", source = Holiday.SOURCE_CUSTOM, enabled = true),
        )
    }

    fun updateHoliday(h: Holiday, date: LocalDate, name: String) = viewModelScope.launch {
        holidayDao.update(h.copy(epochDay = date.toEpochDay(), name = name.trim()))
    }

    fun setHolidayEnabled(h: Holiday, enabled: Boolean) = viewModelScope.launch { holidayDao.update(h.copy(enabled = enabled)) }
    fun deleteHoliday(h: Holiday) = viewModelScope.launch { holidayDao.delete(h) }
    fun setWeekendDays(days: Set<DayOfWeek>) = viewModelScope.launch { settingsRepo.setWeekendDays(days) }
    fun setHolidayApiKey(key: String) = viewModelScope.launch { settingsRepo.setHolidayApiKey(key) }

    private val _filter = MutableStateFlow(HistoryFilter())
    val filter: StateFlow<HistoryFilter> = _filter

    val historyEntries: StateFlow<List<TimeEntry>> = _filter
        .flatMapLatest { f -> f.range().let { dao.between(it.first, it.last + 1) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setFilter(f: HistoryFilter) { _filter.value = f }

    fun clockIn() = viewModelScope.launch {
        if (openEntry.value == null) dao.insert(TimeEntry(clockIn = System.currentTimeMillis()))
    }

    fun clockOut(note: String = "") = viewModelScope.launch {
        openEntry.value?.let { dao.update(it.copy(clockOut = System.currentTimeMillis(), note = note.trim())) }
    }

    fun save(entry: TimeEntry) = viewModelScope.launch {
        if (entry.id == 0L) dao.insert(entry) else dao.update(entry)
    }

    fun delete(entry: TimeEntry) = viewModelScope.launch { dao.delete(entry) }

    fun setOvertimeRate(rate: Double) = viewModelScope.launch { settingsRepo.setOvertimeRate(rate) }
    fun setStandardHours(hours: Double) = viewModelScope.launch { settingsRepo.setStandardHours(hours) }
    fun setTheme(mode: ThemeMode) = viewModelScope.launch { settingsRepo.setTheme(mode) }
    fun setOvertimeMode(mode: OvertimeMode) = viewModelScope.launch { settingsRepo.setOvertimeMode(mode) }
    fun setWorkWindow(startMinutes: Int, endMinutes: Int) =
        viewModelScope.launch { settingsRepo.setWorkWindow(startMinutes, endMinutes) }
}
