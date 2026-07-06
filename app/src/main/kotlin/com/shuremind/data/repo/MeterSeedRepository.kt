package com.shuremind.data.repo

/**
 * D-27: idempotent "seed a monthly reminder the first time this meter_name is ever logged" flag.
 * Backed by a DataStore string set — never re-seeds, even if the user deletes the seeded task.
 */
interface MeterSeedRepository {

    suspend fun isSeeded(meterName: String): Boolean

    suspend fun markSeeded(meterName: String)

    /** M5 export/import (D-30 seed markers): the full idempotency set, read-only round trip. */
    suspend fun getAllSeeded(): Set<String>
}
