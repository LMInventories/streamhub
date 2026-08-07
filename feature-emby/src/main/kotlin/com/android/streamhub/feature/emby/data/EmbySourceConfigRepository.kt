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

// Constructed locally rather than injected - a second unqualified Json binding would collide with
// feature-iptv's, and a qualifier for something this cheap/stateless to build isn't worth it.
private val json = Json { ignoreUnknownKeys = true }

@Singleton
class EmbySourceConfigRepository @Inject constructor(
    @EmbyDataStore private val dataStore: DataStore<Preferences>,
) {
    private val configKey = stringPreferencesKey("emby_source_config_json")

    val configFlow: Flow<EmbySourceConfig?> = dataStore.data.map { prefs ->
        prefs[configKey]?.let { raw ->
            runCatching { json.decodeFromString<EmbySourceConfig>(raw) }.getOrNull()
        }
    }

    suspend fun save(config: EmbySourceConfig) {
        dataStore.edit { it[configKey] = json.encodeToString(config) }
    }

    suspend fun clear() {
        dataStore.edit { it.remove(configKey) }
    }
}
