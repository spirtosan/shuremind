package com.shuremind.data.repo

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

internal class DataStoreBackupSettingsRepository(
    private val dataStore: DataStore<Preferences>,
    private val notifier: AutoBackupSchedulingNotifier = AutoBackupSchedulingNotifier.NONE
) : BackupSettingsRepository {

    override val settings: Flow<BackupSettings> = dataStore.data.map { prefs ->
        BackupSettings(
            autoBackupEnabled = prefs[AUTO_BACKUP_ENABLED] ?: false,
            folderUri = prefs[BACKUP_FOLDER_URI]
        )
    }

    override suspend fun setAutoBackupEnabled(enabled: Boolean) {
        dataStore.edit { it[AUTO_BACKUP_ENABLED] = enabled }
        notifyChanged()
    }

    override suspend fun setFolderUri(uri: String?) {
        dataStore.edit { prefs ->
            if (uri == null) prefs.remove(BACKUP_FOLDER_URI) else prefs[BACKUP_FOLDER_URI] = uri
        }
        notifyChanged()
    }

    private suspend fun notifyChanged() {
        val current = settings.first()
        notifier.onBackupSettingsChanged(current.autoBackupEnabled, current.folderUri)
    }

    private companion object {
        val AUTO_BACKUP_ENABLED = booleanPreferencesKey("auto_backup_enabled")
        val BACKUP_FOLDER_URI = stringPreferencesKey("auto_backup_folder_uri")
    }
}
