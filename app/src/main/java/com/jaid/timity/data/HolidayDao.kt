package com.jaid.timity.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface HolidayDao {
    @Query("SELECT * FROM holidays WHERE deletedAt IS NULL ORDER BY epochDay ASC")
    fun all(): Flow<List<Holiday>>

    /** Keeps existing rows (and the user's enabled toggles or deletions) when the same holiday is synced again. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(holidays: List<Holiday>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(holiday: Holiday): Long

    @Update
    suspend fun update(holiday: Holiday): Int

    @Query("DELETE FROM holidays WHERE id = :id")
    suspend fun hardDelete(id: Long)

    @Query("SELECT * FROM holidays WHERE uid = :uid LIMIT 1")
    suspend fun byUid(uid: String): Holiday?

    @Query("SELECT * FROM holidays WHERE epochDay = :epochDay AND name = :name LIMIT 1")
    suspend fun byDayAndName(epochDay: Long, name: String): Holiday?

    @Query("SELECT * FROM holidays WHERE uid = ''")
    suspend fun missingUid(): List<Holiday>

    @Query("SELECT * FROM holidays WHERE dirty = 1")
    suspend fun dirty(): List<Holiday>

    @Query("UPDATE holidays SET dirty = 0 WHERE uid = :uid AND updatedAt = :updatedAt")
    suspend fun clearDirty(uid: String, updatedAt: Long)
}
