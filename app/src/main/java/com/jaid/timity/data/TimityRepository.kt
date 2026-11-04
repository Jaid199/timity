package com.jaid.timity.data

import android.content.Context
import com.jaid.timity.data.cloud.SyncManager

/**
 * Every local write goes through here so it is stamped with [updatedAt], flagged dirty and queued
 * for the cloud. Deletes are tombstones (deletedAt) so that other devices learn about them.
 */
class TimityRepository private constructor(context: Context) {
    private val db = AppDatabase.get(context)
    val entries: TimeEntryDao = db.timeEntryDao()
    val holidays: HolidayDao = db.holidayDao()
    val periods: WorkPeriodDao = db.workPeriodDao()
    val settings: SettingsRepository = SettingsRepository(context).also { it.onChanged = { changed() } }

    private fun now(): Long = System.currentTimeMillis()
    private fun changed() = SyncManager.requestSync()

    // ---- entries -------------------------------------------------------------------------------

    suspend fun saveEntry(e: TimeEntry) {
        val stamped = e.copy(updatedAt = now(), dirty = true)
        if (e.id == 0L) entries.insert(stamped) else entries.update(stamped)
        changed()
    }

    suspend fun deleteEntry(e: TimeEntry) {
        val t = now()
        entries.update(e.copy(deletedAt = t, updatedAt = t, dirty = true))
        changed()
    }

    // ---- periods -------------------------------------------------------------------------------

    suspend fun savePeriod(p: WorkPeriod) {
        val stamped = p.copy(updatedAt = now(), dirty = true)
        if (p.id == 0L) periods.insert(stamped) else periods.update(stamped)
        changed()
    }

    suspend fun deletePeriod(p: WorkPeriod) {
        val t = now()
        periods.update(p.copy(deletedAt = t, updatedAt = t, dirty = true))
        changed()
    }

    // ---- holidays ------------------------------------------------------------------------------

    /**
     * Adds a day off. A holiday's identity is its (day, name) pair, so adding one that was deleted
     * earlier revives that row instead of creating a duplicate.
     */
    suspend fun addHoliday(epochDay: Long, name: String, type: String, source: String = Holiday.SOURCE_CUSTOM, enabled: Boolean = true) {
        val trimmed = name.trim()
        val uid = Holiday.uidFor(epochDay, trimmed)
        val existing = holidays.byUid(uid) ?: holidays.byDayAndName(epochDay, trimmed)
        val t = now()
        if (existing == null) {
            holidays.insert(Holiday(epochDay = epochDay, name = trimmed, type = type, source = source, enabled = enabled, uid = uid, updatedAt = t, dirty = true))
        } else {
            holidays.update(existing.copy(uid = uid, type = type, source = source, enabled = enabled, deletedAt = null, updatedAt = t, dirty = true))
        }
        changed()
    }

    /** Moving or renaming changes the identity: the old row is tombstoned and a new one created. */
    suspend fun updateHoliday(h: Holiday, epochDay: Long, name: String, type: String) {
        val trimmed = name.trim()
        if (epochDay == h.epochDay && trimmed == h.name) {
            holidays.update(h.copy(type = type, updatedAt = now(), dirty = true))
            changed()
            return
        }
        val t = now()
        holidays.update(h.copy(deletedAt = t, updatedAt = t, dirty = true))
        addHoliday(epochDay, trimmed, type, source = h.source, enabled = h.enabled)
    }

    suspend fun setHolidayEnabled(h: Holiday, enabled: Boolean) {
        holidays.update(h.copy(enabled = enabled, updatedAt = now(), dirty = true))
        changed()
    }

    suspend fun deleteHoliday(h: Holiday) {
        val t = now()
        holidays.update(h.copy(deletedAt = t, updatedAt = t, dirty = true))
        changed()
    }

    /** Calendarific sync. Existing rows, including ones the user deleted or toggled, are left alone. Returns how many were new. */
    suspend fun insertHolidays(fetched: List<Holiday>): Int {
        val t = now()
        val ids = holidays.insertAll(fetched.map { it.copy(updatedAt = t, dirty = true) })
        changed()
        return ids.count { it != -1L }
    }

    /** Rows created before sync existed have no uid yet; derive it so they match other devices. */
    suspend fun repairHolidayUids() {
        holidays.missingUid().forEach { h -> holidays.update(h.copy(uid = Holiday.uidFor(h.epochDay, h.name))) }
    }

    companion object {
        @Volatile private var instance: TimityRepository? = null

        fun get(context: Context): TimityRepository = instance ?: synchronized(this) {
            instance ?: TimityRepository(context.applicationContext).also {
                instance = it
                SyncManager.attach(context.applicationContext, it)
            }
        }
    }
}
