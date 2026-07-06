package com.shuremind.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.shuremind.data.entity.MeterReadingEntity
import kotlinx.coroutines.flow.Flow

@Dao
internal interface MeterReadingDao {

    @Insert
    suspend fun insert(reading: MeterReadingEntity)

    @Insert
    suspend fun insertAll(readings: List<MeterReadingEntity>)

    @Query("SELECT * FROM meter_readings WHERE meter_name = :meterName ORDER BY recorded_at DESC LIMIT 1")
    suspend fun getLatest(meterName: String): MeterReadingEntity?

    @Query("SELECT * FROM meter_readings WHERE meter_name = :meterName ORDER BY recorded_at DESC")
    fun observeForMeter(meterName: String): Flow<List<MeterReadingEntity>>

    @Query("SELECT * FROM meter_readings ORDER BY meter_name ASC, recorded_at DESC")
    fun observeAll(): Flow<List<MeterReadingEntity>>

    /** M5 import (D-31 replace-all). observeAll() already covers export (no soft delete on this table). */
    @Query("DELETE FROM meter_readings")
    suspend fun deleteAll()
}
