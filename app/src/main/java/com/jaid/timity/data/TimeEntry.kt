package com.jaid.timity.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/** One work session. Times are epoch milliseconds. [clockOut] is null while the session is open. */
@Entity(tableName = "entries", indices = [Index(value = ["uid"], unique = true)])
data class TimeEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val clockIn: Long,
    val clockOut: Long? = null,
    /** Free-text comment, typically written at clock-out. */
    @ColumnInfo(defaultValue = "") val note: String = "",
    /** Stable id shared with the cloud copy; generated on the phone so rows can be created offline. */
    @ColumnInfo(defaultValue = "") val uid: String = newUid(),
    /** Epoch millis of the last local change; the cloud keeps whichever device wrote last. */
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = 0,
    /** Tombstone: set instead of deleting so the deletion reaches other devices. */
    val deletedAt: Long? = null,
    /** True until the row has been pushed to the cloud. */
    @ColumnInfo(defaultValue = "1") val dirty: Boolean = true,
) {
    val isOpen: Boolean get() = clockOut == null
}

fun newUid(): String = UUID.randomUUID().toString()
