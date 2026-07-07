package com.android.streamhub.feature.iptv.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import javax.inject.Inject

class IptvSourceConfigRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val json: Json,
) {
    private val configKey = stringPreferencesKey("iptv_source_config_json")

    val configFlow: Flow<IptvSourceConfig?> = dataStore.data.map { prefs ->
        prefs[configKey]?.let { raw ->
            runCatching { json.decodeFromString<IptvSourceConfig>(raw) }.getOrNull()
        }
    }

    suspend fun save(config: IptvSourceConfig) {
        dataStore.edit { it[configKey] = json.encodeToString(config) }
    }

    suspend fun clear() {
        dataStore.edit { it.remove(configKey) }
    }
}
