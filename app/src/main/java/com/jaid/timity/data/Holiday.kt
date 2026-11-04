package com.jaid.timity.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.util.UUID

/** A day on which all time worked counts as overtime. */
@Entity(
    tableName = "holidays",
    indices = [Index(value = ["epochDay", "name"], unique = true), Index(value = ["uid"])],
)
data class Holiday(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val name: String,
    /** Calendarific primary type, e.g. "National holiday"; "Custom" for user-added. */
    val type: String,
    val source: String,
    val enabled: Boolean,
    /**
     * Derived from [epochDay] and [name], so two phones that both sync Calendarific produce the same
     * cloud row instead of fighting over the unique (day, name) pair. Blank only on rows created before
     * sync existed; [TimityRepository.repairHolidayUids] fills those in.
     */
    @ColumnInfo(defaultValue = "") val uid: String = uidFor(epochDay, name),
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = 0,
    val deletedAt: Long? = null,
    @ColumnInfo(defaultValue = "1") val dirty: Boolean = true,
) {
    val date: LocalDate get() = LocalDate.ofEpochDay(epochDay)

    companion object {
        const val SOURCE_API = "calendarific"
        const val SOURCE_CUSTOM = "custom"

        /** Types for user-added days. Stored as-is; shown through string resources. */
        const val TYPE_CUSTOM = "Custom"
        const val TYPE_SICK = "Sick day"
        const val TYPE_OFF = "Day off"
        val CUSTOM_TYPES = listOf(TYPE_CUSTOM, TYPE_SICK, TYPE_OFF)

        fun uidFor(epochDay: Long, name: String): String =
            UUID.nameUUIDFromBytes("timity-holiday:$epochDay:${name.trim()}".toByteArray()).toString()
    }
}
