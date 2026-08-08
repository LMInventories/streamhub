package com.android.streamhub.feature.emby.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.android.streamhub.feature.emby.di.EmbyDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

// Constructed locally rather than injected - same reasoning as EmbySourceConfigRepository's own
// copy: a second unqualified Json binding would collide with feature-iptv's.
private val json = Json { ignoreUnknownKeys = true }

@Singleton
class EmbyAppSettingsRepository @Inject constructor(
    @EmbyDataStore private val dataStore: DataStore<Preferences>,
) {
    // Same DataStore file as EmbySourceConfigRepository (different key) - EmbyDataStoreModule's
    // "emby_settings" Preferences file already holds more than one key once this is added, the
    // same way feature-jellyfin's DataStore holds both JellyfinSourceConfig and
    // JellyfinAppSettings under separate keys.
    private val settingsKey = stringPreferencesKey("emby_app_settings_json")

    val settingsFlow: Flow<EmbyAppSettings> = dataStore.data.map { prefs ->
        prefs[settingsKey]?.let { raw ->
            runCatching { json.decodeFromString<EmbyAppSettings>(raw) }.getOrNull()
        } ?: EmbyAppSettings()
    }

    suspend fun update(transform: (EmbyAppSettings) -> EmbyAppSettings) {
        dataStore.edit { prefs ->
            val current = prefs[settingsKey]?.let { raw ->
                runCatching { json.decodeFromString<EmbyAppSettings>(raw) }.getOrNull()
            } ?: EmbyAppSettings()
            prefs[settingsKey] = json.encodeToString(transform(current))
        }
    }
}
