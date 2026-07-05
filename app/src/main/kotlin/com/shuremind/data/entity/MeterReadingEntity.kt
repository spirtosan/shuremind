package com.shuremind.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "meter_readings")
data class MeterReadingEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "meter_name") val meterName: String,
    val value: Double,
    @ColumnInfo(name = "recorded_at") val recordedAt: Long
)
