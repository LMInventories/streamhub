package com.android.streamhub.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.android.streamhub.di.AppUiDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val json = Json { ignoreUnknownKeys = true }

@Singleton
class AppUiSettingsRepository @Inject constructor(
    @AppUiDataStore private val dataStore: DataStore<Preferences>,
) {
    private val settingsKey = stringPreferencesKey("app_ui_settings_json")

    val settingsFlow: Flow<AppUiSettings> = dataStore.data.map { prefs ->
        prefs[settingsKey]?.let { raw ->
            runCatching { json.decodeFromString<AppUiSettings>(raw) }.getOrNull()
        } ?: AppUiSettings()
    }

    suspend fun update(transform: (AppUiSettings) -> AppUiSettings) {
        dataStore.edit { prefs ->
            val current = prefs[settingsKey]?.let { raw ->
                runCatching { json.decodeFromString<AppUiSettings>(raw) }.getOrNull()
            } ?: AppUiSettings()
            prefs[settingsKey] = json.encodeToString(transform(current))
        }
    }
}
