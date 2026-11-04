package com.jaid.timity.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TimeEntryDao {
    @Query("SELECT * FROM entries WHERE clockOut IS NULL AND deletedAt IS NULL ORDER BY clockIn DESC LIMIT 1")
    fun openEntry(): Flow<TimeEntry?>

    @Query("SELECT * FROM entries WHERE clockIn >= :from AND clockIn < :to AND deletedAt IS NULL ORDER BY clockIn DESC")
    fun between(from: Long, to: Long): Flow<List<TimeEntry>>

    @Insert
    suspend fun insert(entry: TimeEntry): Long

    @Update
    suspend fun update(entry: TimeEntry)

    @Query("SELECT * FROM entries WHERE uid = :uid LIMIT 1")
    suspend fun byUid(uid: String): TimeEntry?

    @Query("SELECT * FROM entries WHERE dirty = 1")
    suspend fun dirty(): List<TimeEntry>

    /** Only clears the flag if the row has not changed again since it was pushed. */
    @Query("UPDATE entries SET dirty = 0 WHERE uid = :uid AND updatedAt = :updatedAt")
    suspend fun clearDirty(uid: String, updatedAt: Long)
}
