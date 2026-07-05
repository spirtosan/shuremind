package com.shuremind.data.repo

import com.shuremind.data.dao.MeterReadingDao
import com.shuremind.data.entity.MeterReadingEntity
import kotlinx.coroutines.flow.Flow

/** UI/ViewModels talk only to this repo, never MeterReadingDao directly (from M2 on). */
class MeterRepository internal constructor(private val meterReadingDao: MeterReadingDao) {

    suspend fun record(reading: MeterReadingEntity) = meterReadingDao.insert(reading)

    suspend fun getLatest(meterName: String): MeterReadingEntity? = meterReadingDao.getLatest(meterName)

    fun observeForMeter(meterName: String): Flow<List<MeterReadingEntity>> = meterReadingDao.observeForMeter(meterName)

    /** All readings across every meter, grouped by name (newest first within each), for the Meter readings screen (D-27). */
    fun observeAll(): Flow<List<MeterReadingEntity>> = meterReadingDao.observeAll()
}
