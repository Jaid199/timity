package com.jaid.timity.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkPeriodDao {
    @Query("SELECT * FROM work_periods WHERE deletedAt IS NULL ORDER BY startEpochDay ASC")
    fun all(): Flow<List<WorkPeriod>>

    @Insert
    suspend fun insert(period: WorkPeriod): Long

    @Update
    suspend fun update(period: WorkPeriod)

    @Query("SELECT * FROM work_periods WHERE uid = :uid LIMIT 1")
    suspend fun byUid(uid: String): WorkPeriod?

    @Query("SELECT * FROM work_periods WHERE dirty = 1")
    suspend fun dirty(): List<WorkPeriod>

    @Query("UPDATE work_periods SET dirty = 0 WHERE uid = :uid AND updatedAt = :updatedAt")
    suspend fun clearDirty(uid: String, updatedAt: Long)
}
