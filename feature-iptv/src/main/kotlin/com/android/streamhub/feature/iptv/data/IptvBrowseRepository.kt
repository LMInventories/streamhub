package com.android.streamhub.feature.iptv.data

import kotlinx.coroutines.flow.first
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

data class IptvCategoryInfo(val id: String, val name: String)

data class IptvChannelInfo(
    // Xtream's stream_id - identifies the channel for playback (live stream URL, get_short_epg).
    val id: String,
    val name: String,
    val logoUrl: String?,
    val streamUrl: String,
    // Xtream's epg_channel_id - a *separate* identifier from stream_id, and the one that
    // actually matches xmltv.php's <programme channel="..."> attribute. null for M3U, where
    // there's no such split - id (already tvg-id-derived, see M3uParser) serves both purposes.
    val epgChannelId: String? = null,
)

/** The id to look up this channel's bulk EPG grid data by - never id directly, which for Xtream is stream_id and won't match xmltv.php's channel identifiers. */
val IptvChannelInfo.epgKey: String get() = epgChannelId ?: id

private const val UNCATEGORIZED_ID = "uncategorized"

/**
 * Category/channel/EPG browsing for the dedicated Live TV screen - richer than what
 * MediaSource.browse()'s flat-list contract models, so this sits alongside IptvMediaSource
 * rather than through it (both read the same underlying remote data sources).
 */
@Singleton
class IptvBrowseRepository @Inject constructor(
    private val configRepository: IptvSourceConfigRepository,
    private val xtreamRemoteDataSource: XtreamRemoteDataSource,
    private val m3uRemoteDataSource: M3uRemoteDataSource,
) {
    // M3U's XMLTV guide is a single (potentially large) file - fetch once per epgUrl and reuse,
    // rather than re-downloading it every time the focused channel changes.
    private var cachedEpgUrl: String? = null
    private var cachedEpgByChannelId: Map<String, List<EpgProgram>> = emptyMap()

    // Categories/channels weren't cached at all before this - every category tap re-hit the
    // network (an Xtream API call, or for M3U, a full re-download+re-parse of the whole
    // playlist just to filter it client-side by category). Cleared by invalidateCache(), which
    // "Update Playlist" and the auto-update setting both already call through to.
    private var cachedCategories: List<IptvCategoryInfo>? = null
    private val cachedChannelsByCategory = mutableMapOf<String, List<IptvChannelInfo>>()
    // The one raw fetch+parse of the M3U playlist that both getCategories() and every
    // getChannels() call now share, instead of each doing its own independent fetch+parse.
    private var cachedM3uChannels: List<M3uChannel>? = null

    suspend fun getCategories(): List<IptvCategoryInfo> {
        cachedCategories?.let { return it }
        val config = configRepository.configFlow.first() ?: return emptyList()
        val result = when (config) {
            is IptvSourceConfig.Xtream ->
                xtreamRemoteDataSource.getLiveCategories(config).map { IptvCategoryInfo(it.categoryId, it.categoryName) }
            is IptvSourceConfig.M3u ->
                m3uChannels(config.playlistUrl)
                    .map { it.groupTitle?.takeIf(String::isNotBlank) ?: UNCATEGORIZED_ID }
                    .distinct()
                    .map { IptvCategoryInfo(id = it, name = if (it == UNCATEGORIZED_ID) "Uncategorized" else it) }
        }
        cachedCategories = result
        return result
    }

    suspend fun getChannels(categoryId: String): List<IptvChannelInfo> {
        cachedChannelsByCategory[categoryId]?.let { return it }
        val config = configRepository.configFlow.first() ?: return emptyList()
        val result = when (config) {
            is IptvSourceConfig.Xtream ->
                xtreamRemoteDataSource.getLiveStreams(config, categoryId).map {
                    IptvChannelInfo(
                        id = it.streamId,
                        name = it.name,
                        logoUrl = it.streamIcon,
                        streamUrl = config.liveStreamUrl(it.streamId),
                        epgChannelId = it.epgChannelId?.takeIf(String::isNotBlank),
                    )
                }
            is IptvSourceConfig.M3u ->
                m3uChannels(config.playlistUrl)
                    .filter { (it.groupTitle?.takeIf(String::isNotBlank) ?: UNCATEGORIZED_ID) == categoryId }
                    .map { IptvChannelInfo(id = it.id, name = it.name, logoUrl = it.logoUrl, streamUrl = it.streamUrl) }
        }
        cachedChannelsByCategory[categoryId] = result
        return result
    }

    private suspend fun m3uChannels(playlistUrl: String): List<M3uChannel> {
        cachedM3uChannels?.let { return it }
        val fetched = m3uRemoteDataSource.fetchChannels(playlistUrl)
        cachedM3uChannels = fetched
        return fetched
    }

    /** Fetched on demand for whichever single channel is focused (e.g. in the mini-player) - not for a whole list. */
    suspend fun getNowNext(channelId: String, now: Instant = Instant.now()): Pair<EpgProgram?, EpgProgram?> {
        val config = configRepository.configFlow.first() ?: return null to null
        val programs = when (config) {
            is IptvSourceConfig.Xtream ->
                xtreamRemoteDataSource.getShortEpg(config, channelId)
            is IptvSourceConfig.M3u ->
                config.epgUrl?.let { epgByChannelId(it)[channelId] }.orEmpty()
        }
        val sorted = programs.sortedBy { it.startAt }
        val current = sorted.firstOrNull { it.isCurrentAt(now) }
        val next = sorted.firstOrNull { it.startAt > now }
        return current to next
    }

    private suspend fun epgByChannelId(epgUrl: String): Map<String, List<EpgProgram>> {
        if (epgUrl == cachedEpgUrl) return cachedEpgByChannelId
        val fetched = runCatching { m3uRemoteDataSource.fetchEpg(epgUrl) }.getOrDefault(emptyMap())
        cachedEpgUrl = epgUrl
        cachedEpgByChannelId = fetched
        return fetched
    }

    /** Drops every in-memory cache (categories, channels-by-category, the raw M3U playlist, the M3U EPG) - used by "Update Playlist" in Settings and the auto-update setting. */
    fun invalidateCache() {
        cachedEpgUrl = null
        cachedEpgByChannelId = emptyMap()
        cachedCategories = null
        cachedChannelsByCategory.clear()
        cachedM3uChannels = null
    }
}
