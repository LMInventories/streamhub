package com.android.streamhub.feature.iptv.data.epg

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import com.android.streamhub.feature.iptv.data.EpgProgram
import com.android.streamhub.feature.iptv.data.IptvSourceConfig
import com.android.streamhub.feature.iptv.data.IptvSourceConfigRepository
import com.android.streamhub.feature.iptv.data.XmlTvParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bulk EPG for the multi-day grid - deliberately separate from IptvBrowseRepository's
 * per-channel on-demand now/next (which stays on Xtream's lighter get_short_epg call for that
 * use case). A 7-day guide needs the full XMLTV dump either way - Xtream serves one at
 * xmltv.php the same as a standalone M3U+EPG-URL setup - so both source types funnel through
 * the same parser and the same Room-backed cache here.
 */
@Singleton
class EpgGridRepository @Inject constructor(
    private val configRepository: IptvSourceConfigRepository,
    private val dao: EpgDao,
    private val okHttpClient: OkHttpClient,
    private val dataStore: DataStore<Preferences>,
) {
    private val lastRefreshedKey = longPreferencesKey("epg_grid_last_refreshed_epoch_seconds")

    // A bit under 24h so a daily refresh doesn't creep later and later each day.
    private val refreshIntervalSeconds = TimeUnit.HOURS.toSeconds(20)

    /** Fetches and stores the full guide if the cache is missing or stale. Returns false if there's nothing to fetch (no EPG configured) or the fetch failed. */
    suspend fun ensureFresh(): Boolean = withContext(Dispatchers.IO) {
        val config = configRepository.configFlow.first() ?: return@withContext false
        val xmltvUrl = xmltvUrlFor(config) ?: return@withContext false

        val lastRefreshed = dataStore.data.map { it[lastRefreshedKey] ?: 0L }.first()
        val now = Instant.now().epochSecond
        if (now - lastRefreshed < refreshIntervalSeconds && dao.count() > 0) return@withContext true

        val xml = fetchText(xmltvUrl) ?: return@withContext dao.count() > 0
        val programmes = XmlTvParser.parse(xml)
        if (programmes.isEmpty()) return@withContext dao.count() > 0

        dao.clearAll()
        dao.insertAll(
            programmes.map {
                ProgrammeEntity(
                    channelId = it.channelId,
                    startAtEpochSeconds = it.program.startAt.epochSecond,
                    endAtEpochSeconds = it.program.endAt.epochSecond,
                    title = it.program.title,
                    description = it.program.description,
                )
            },
        )
        dataStore.edit { it[lastRefreshedKey] = now }
        true
    }

    suspend fun getGrid(channelIds: List<String>, from: Instant, to: Instant): Map<String, List<EpgProgram>> {
        if (channelIds.isEmpty()) return emptyMap()
        return dao.getProgrammes(channelIds, from.epochSecond, to.epochSecond)
            .groupBy(keySelector = { it.channelId }, valueTransform = { it.toEpgProgram() })
    }

    private fun xmltvUrlFor(config: IptvSourceConfig): String? = when (config) {
        is IptvSourceConfig.Xtream -> config.baseUrl.toHttpUrlOrNull()
            ?.newBuilder()
            ?.addPathSegment("xmltv.php")
            ?.addQueryParameter("username", config.username)
            ?.addQueryParameter("password", config.password)
            ?.build()
            ?.toString()
        is IptvSourceConfig.M3u -> config.epgUrl
    }

    private fun fetchText(url: String): String? = runCatching {
        val request = Request.Builder().url(url).build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null
            response.body?.string()
        }
    }.getOrNull()
}
