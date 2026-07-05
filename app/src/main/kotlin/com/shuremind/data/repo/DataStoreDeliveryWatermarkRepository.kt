package com.shuremind.data.repo

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.flow.first

internal class DataStoreDeliveryWatermarkRepository(private val dataStore: DataStore<Preferences>) : DeliveryWatermarkRepository {

    override suspend fun get(): Long {
        val existing = dataStore.data.first()[LAST_HANDLED_AT]
        if (existing != null) return existing
        val now = System.currentTimeMillis()
        dataStore.edit { it[LAST_HANDLED_AT] = now }
        return now
    }

    override suspend fun advanceTo(instant: Long) {
        dataStore.edit { it[LAST_HANDLED_AT] = instant }
    }

    private companion object {
        val LAST_HANDLED_AT = longPreferencesKey("last_handled_at")
    }
}
