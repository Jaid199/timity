package com.jaid.timity.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** One work session. Times are epoch milliseconds. [clockOut] is null while the session is open. */
@Entity(tableName = "entries")
data class TimeEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val clockIn: Long,
    val clockOut: Long? = null,
) {
    val isOpen: Boolean get() = clockOut == null
}
