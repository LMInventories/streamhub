package com.android.streamhub.feature.iptv.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

// Must be a true singleton - refreshEvents is in-memory pub/sub, and every ViewModel that
// collects it needs to observe the same instance for "Update Playlist" in Settings to actually
// reach Live TV/VOD's active screens.
@Singleton
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

    // Re-saving the same config wouldn't change configFlow's emitted value (equal data class),
    // so "Update Playlist" needs its own explicit signal - this is that signal, separate from
    // "the source itself changed".
    private val _refreshEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val refreshEvents: SharedFlow<Unit> = _refreshEvents.asSharedFlow()

    suspend fun save(config: IptvSourceConfig) {
        dataStore.edit { it[configKey] = json.encodeToString(config) }
    }

    suspend fun clear() {
        dataStore.edit { it.remove(configKey) }
    }

    suspend fun requestRefresh() {
        _refreshEvents.emit(Unit)
    }
}
