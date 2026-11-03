package com.jaid.timity.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TimeEntryDao {
    @Query("SELECT * FROM entries WHERE clockOut IS NULL ORDER BY clockIn DESC LIMIT 1")
    fun openEntry(): Flow<TimeEntry?>

    @Query("SELECT * FROM entries WHERE clockIn >= :from AND clockIn < :to ORDER BY clockIn DESC")
    fun between(from: Long, to: Long): Flow<List<TimeEntry>>

    @Insert
    suspend fun insert(entry: TimeEntry): Long

    @Update
    suspend fun update(entry: TimeEntry)

    @Delete
    suspend fun delete(entry: TimeEntry)
}
