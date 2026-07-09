package com.android.streamhub.feature.iptv.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val json = Json { ignoreUnknownKeys = true }

@Singleton
class IptvAppSettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private val settingsKey = stringPreferencesKey("iptv_app_settings_json")

    val settingsFlow: Flow<IptvAppSettings> = dataStore.data.map { prefs ->
        prefs[settingsKey]?.let { raw ->
            runCatching { json.decodeFromString<IptvAppSettings>(raw) }.getOrNull()
        } ?: IptvAppSettings()
    }

    suspend fun update(transform: (IptvAppSettings) -> IptvAppSettings) {
        dataStore.edit { prefs ->
            val current = prefs[settingsKey]?.let { raw ->
                runCatching { json.decodeFromString<IptvAppSettings>(raw) }.getOrNull()
            } ?: IptvAppSettings()
            prefs[settingsKey] = json.encodeToString(transform(current))
        }
    }
}
