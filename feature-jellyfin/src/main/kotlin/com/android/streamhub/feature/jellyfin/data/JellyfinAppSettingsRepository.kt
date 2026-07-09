package com.android.streamhub.feature.jellyfin.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.android.streamhub.feature.jellyfin.di.JellyfinDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

// Constructed locally rather than injected - same reasoning as JellyfinSourceConfigRepository's
// own copy: a second unqualified Json binding would collide with feature-iptv's.
private val json = Json { ignoreUnknownKeys = true }

@Singleton
class JellyfinAppSettingsRepository @Inject constructor(
    @JellyfinDataStore private val dataStore: DataStore<Preferences>,
) {
    // Same DataStore file as JellyfinSourceConfigRepository (different key) - one Preferences
    // file per feature module already holds more than one key elsewhere in this app (see
    // feature-iptv's settings), no need for a second DataStore instance just for this.
    private val settingsKey = stringPreferencesKey("jellyfin_app_settings_json")

    val settingsFlow: Flow<JellyfinAppSettings> = dataStore.data.map { prefs ->
        prefs[settingsKey]?.let { raw ->
            runCatching { json.decodeFromString<JellyfinAppSettings>(raw) }.getOrNull()
        } ?: JellyfinAppSettings()
    }

    suspend fun update(transform: (JellyfinAppSettings) -> JellyfinAppSettings) {
        dataStore.edit { prefs ->
            val current = prefs[settingsKey]?.let { raw ->
                runCatching { json.decodeFromString<JellyfinAppSettings>(raw) }.getOrNull()
            } ?: JellyfinAppSettings()
            prefs[settingsKey] = json.encodeToString(transform(current))
        }
    }
}
