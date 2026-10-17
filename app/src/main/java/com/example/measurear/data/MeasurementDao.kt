package com.example.measurear.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MeasurementDao {
    @Query("SELECT * FROM measurements ORDER BY timestamp DESC")
    fun observeAllMeasurements(): Flow<List<MeasurementEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMeasurement(entity: MeasurementEntity): Long

    @Delete
    suspend fun deleteMeasurement(entity: MeasurementEntity)

    @Query("DELETE FROM measurements")
    suspend fun deleteAll()
}
