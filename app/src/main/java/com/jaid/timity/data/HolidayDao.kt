package com.jaid.timity.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface HolidayDao {
    @Query("SELECT * FROM holidays ORDER BY epochDay ASC")
    fun all(): Flow<List<Holiday>>

    /** Keeps existing rows (and the user's enabled toggles) when the same holiday is synced again. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(holidays: List<Holiday>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(holiday: Holiday): Long

    /** IGNORE so moving a holiday onto a date that already has one with the same name does not throw. */
    @Update(onConflict = OnConflictStrategy.IGNORE)
    suspend fun update(holiday: Holiday): Int

    @Delete
    suspend fun delete(holiday: Holiday)
}
