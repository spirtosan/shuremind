package com.shuremind.data.repo

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first

internal class DataStoreWeeklyReviewSeedRepository(private val dataStore: DataStore<Preferences>) : WeeklyReviewSeedRepository {

    override suspend fun isSeeded(): Boolean = dataStore.data.first()[TASK_ID] != null

    override suspend fun markSeeded(taskId: String) {
        dataStore.edit { it[TASK_ID] = taskId }
    }

    override suspend fun seededTaskId(): String? = dataStore.data.first()[TASK_ID]

    private companion object {
        val TASK_ID = stringPreferencesKey("weekly_review_task_id")
    }
}
