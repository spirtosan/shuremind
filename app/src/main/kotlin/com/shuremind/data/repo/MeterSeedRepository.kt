package com.shuremind.data.repo

/**
 * D-27: idempotent "seed a monthly reminder the first time this meter_name is ever logged" flag.
 * Backed by a DataStore string set — never re-seeds, even if the user deletes the seeded task.
 */
interface MeterSeedRepository {

    suspend fun isSeeded(meterName: String): Boolean

    suspend fun markSeeded(meterName: String)
}
