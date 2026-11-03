package com.jaid.timity.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

/** A day on which all time worked counts as overtime. */
@Entity(tableName = "holidays", indices = [Index(value = ["epochDay", "name"], unique = true)])
data class Holiday(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val name: String,
    /** Calendarific primary type, e.g. "National holiday"; "Custom" for user-added. */
    val type: String,
    val source: String,
    val enabled: Boolean,
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
    }
}
