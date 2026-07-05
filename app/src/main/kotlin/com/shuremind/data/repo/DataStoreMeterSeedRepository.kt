package com.shuremind.data.repo

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.first

internal class DataStoreMeterSeedRepository(private val dataStore: DataStore<Preferences>) : MeterSeedRepository {

    override suspend fun isSeeded(meterName: String): Boolean =
        meterName in (dataStore.data.first()[SEEDED_METER_NAMES] ?: emptySet())

    override suspend fun markSeeded(meterName: String) {
        dataStore.edit { prefs -> prefs[SEEDED_METER_NAMES] = (prefs[SEEDED_METER_NAMES] ?: emptySet()) + meterName }
    }

    private companion object {
        val SEEDED_METER_NAMES = stringSetPreferencesKey("seeded_meter_names")
    }
}
