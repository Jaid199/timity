package com.jaid.timity.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkPeriodDao {
    @Query("SELECT * FROM work_periods ORDER BY startEpochDay ASC")
    fun all(): Flow<List<WorkPeriod>>

    @Insert
    suspend fun insert(period: WorkPeriod): Long

    @Update
    suspend fun update(period: WorkPeriod)

    @Delete
    suspend fun delete(period: WorkPeriod)
}
