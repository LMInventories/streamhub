package com.android.streamhub.feature.jellyfin.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.android.streamhub.feature.jellyfin.di.JellyfinDataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

// Constructed locally rather than injected - same reasoning as JellyfinAppSettingsRepository's
// own copy: a second unqualified Json binding would collide with feature-iptv's.
private val json = Json { ignoreUnknownKeys = true }

/**
 * Persists the last successfully-loaded Jellyfin home screen (Continue Watching/Next Up/
 * Favourites/each library's Latest) to disk, so a cold app start can show that immediately
 * instead of a blank spinner while the real network fetch (JellyfinHomeViewModel.loadHome())
 * runs in the background and replaces it - the in-memory-only caching IptvBrowseRepository/
 * IptvVodRepository use doesn't help here since a Hilt Singleton is just as dead as everything
 * else once the process is killed, which is when this was actually being felt (every cold start
 * paid for a full re-fetch of every section before showing anything).
 */
@Singleton
class JellyfinHomeCacheRepository @Inject constructor(
    @JellyfinDataStore private val dataStore: DataStore<Preferences>,
) {
    private val cacheKey = stringPreferencesKey("jellyfin_home_cache_json")

    suspend fun readCache(): List<JellyfinHomeSection>? {
        val raw = dataStore.data.first()[cacheKey] ?: return null
        return runCatching { json.decodeFromString<List<JellyfinHomeSection>>(raw) }.getOrNull()
    }

    suspend fun writeCache(sections: List<JellyfinHomeSection>) {
        dataStore.edit { it[cacheKey] = json.encodeToString(sections) }
    }
}
