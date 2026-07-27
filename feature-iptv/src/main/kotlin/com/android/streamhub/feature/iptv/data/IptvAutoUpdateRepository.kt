package com.android.streamhub.feature.iptv.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.android.streamhub.feature.iptv.data.epg.EpgGridRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns both the auto-update policy (persisted setting) and the shared "do a full playlist
 * refresh" sequence - previously duplicated inline in IptvSettingsViewModel.updatePlaylist(),
 * now shared between the manual "Update Playlist" button (forceRefreshNow, unconditional) and
 * the auto-update check (checkAndRefreshIfDue, gated on the configured policy).
 */
@Singleton
class IptvAutoUpdateRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val json: Json,
    private val configRepository: IptvSourceConfigRepository,
    private val browseRepository: IptvBrowseRepository,
    private val vodRepository: IptvVodRepository,
    private val epgGridRepository: EpgGridRepository,
    private val mediaSource: IptvMediaSource,
) {
    private val settingKey = stringPreferencesKey("iptv_auto_update_setting_json")
    private val lastRefreshKey = longPreferencesKey("iptv_auto_update_last_refresh_epoch_seconds")

    val settingFlow: Flow<AutoUpdateSetting> = dataStore.data.map { prefs ->
        prefs[settingKey]?.let { raw -> runCatching { json.decodeFromString<AutoUpdateSetting>(raw) }.getOrNull() } ?: AutoUpdateSetting()
    }

    suspend fun saveSetting(setting: AutoUpdateSetting) {
        dataStore.edit { it[settingKey] = json.encodeToString(setting) }
    }

    /** Drops every IPTV cache and re-signals active screens to refetch - unconditional, used only by the manual "Update Playlist" button. checkAndRefreshIfDue()'s automatic tick uses refreshIfDue() below instead, which doesn't bypass Cache Duration. */
    suspend fun forceRefreshNow() {
        mediaSource.invalidateCache()
        browseRepository.invalidateCache()
        vodRepository.invalidateCache()
        runCatching { epgGridRepository.ensureFresh(forceRefresh = true) }
        configRepository.requestRefresh()
        dataStore.edit { it[lastRefreshKey] = Instant.now().epochSecond }
    }

    /**
     * The automatic policy tick's own version of forceRefreshNow() - deliberately lighter. Channel
     * list (IptvBrowseRepository) and the EPG guide (EpgGridRepository) now track their own
     * Cache Duration-based freshness independently of whether/when this fires, so forcing them here
     * too would defeat that setting entirely (this used to call forceRefreshNow() directly, which
     * unconditionally bypassed both - the reported "EPG keeps re-downloading no matter what" bug).
     * VOD has no such TTL of its own yet, so it keeps today's "auto-update tick invalidates it"
     * behavior; re-validating the source config itself (requestRefresh) is a different concern
     * from cache staleness and stays unconditional too.
     */
    private suspend fun refreshIfDue() {
        vodRepository.invalidateCache()
        configRepository.requestRefresh()
        dataStore.edit { it[lastRefreshKey] = Instant.now().epochSecond }
    }

    /**
     * Called once per app foreground - refreshes only if the configured auto-update policy says
     * it's due. No-ops silently if there's no source configured or the policy is Off, so this is
     * always safe to call unconditionally from an app-level lifecycle hook.
     */
    suspend fun checkAndRefreshIfDue() {
        if (configRepository.configFlow.first() == null) return
        val setting = settingFlow.first()
        if (setting.mode == AutoUpdateMode.OFF) return

        val lastRefresh = dataStore.data.map { it[lastRefreshKey] ?: 0L }.first()
        val now = Instant.now().epochSecond
        val due = when (setting.mode) {
            AutoUpdateMode.OFF -> false
            AutoUpdateMode.ON_APP_OPEN -> true
            AutoUpdateMode.EVERY_N_DAYS -> now - lastRefresh >= TimeUnit.DAYS.toSeconds(setting.days.toLong())
            AutoUpdateMode.EVERY_N_HOURS -> now - lastRefresh >= TimeUnit.HOURS.toSeconds(setting.hours.toLong())
        }
        if (due) refreshIfDue()
    }
}
