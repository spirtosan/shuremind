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

    @Query("SELECT * FROM meter_readings WHERE meter_name = :meterName ORDER BY recorded_at DESC LIMIT 1")
    suspend fun getLatest(meterName: String): MeterReadingEntity?

    @Query("SELECT * FROM meter_readings WHERE meter_name = :meterName ORDER BY recorded_at DESC")
    fun observeForMeter(meterName: String): Flow<List<MeterReadingEntity>>
}
