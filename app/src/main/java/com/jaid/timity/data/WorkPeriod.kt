package com.jaid.timity.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

/** A date range, such as Ramadan, with its own working-hours window and standard day length. */
@Entity(tableName = "work_periods", indices = [Index(value = ["uid"], unique = true)])
data class WorkPeriod(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val startEpochDay: Long,
    val endEpochDay: Long,
    /** Minutes from midnight. */
    val workStart: Int,
    val workEnd: Int,
    val standardHours: Double,
    /** Switched off: the period is kept but its hours are not applied. */
    @ColumnInfo(defaultValue = "1") val enabled: Boolean = true,
    @ColumnInfo(defaultValue = "") val uid: String = newUid(),
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = 0,
    val deletedAt: Long? = null,
    @ColumnInfo(defaultValue = "1") val dirty: Boolean = true,
) {
    val start: LocalDate get() = LocalDate.ofEpochDay(startEpochDay)
    val end: LocalDate get() = LocalDate.ofEpochDay(endEpochDay)
    fun contains(date: LocalDate): Boolean = !date.isBefore(start) && !date.isAfter(end)
}
