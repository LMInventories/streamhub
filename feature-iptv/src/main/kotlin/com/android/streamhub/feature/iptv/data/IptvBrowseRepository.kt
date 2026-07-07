package com.android.streamhub.feature.iptv.data

import kotlinx.coroutines.flow.first
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

data class IptvCategoryInfo(val id: String, val name: String)

data class IptvChannelInfo(
    val id: String,
    val name: String,
    val logoUrl: String?,
    val streamUrl: String,
)

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

    suspend fun getCategories(): List<IptvCategoryInfo> {
        val config = configRepository.configFlow.first() ?: return emptyList()
        return when (config) {
            is IptvSourceConfig.Xtream ->
                xtreamRemoteDataSource.getLiveCategories(config).map { IptvCategoryInfo(it.categoryId, it.categoryName) }
            is IptvSourceConfig.M3u ->
                m3uRemoteDataSource.fetchChannels(config.playlistUrl)
                    .map { it.groupTitle?.takeIf(String::isNotBlank) ?: UNCATEGORIZED_ID }
                    .distinct()
                    .map { IptvCategoryInfo(id = it, name = if (it == UNCATEGORIZED_ID) "Uncategorized" else it) }
        }
    }

    suspend fun getChannels(categoryId: String): List<IptvChannelInfo> {
        val config = configRepository.configFlow.first() ?: return emptyList()
        return when (config) {
            is IptvSourceConfig.Xtream ->
                xtreamRemoteDataSource.getLiveStreams(config, categoryId).map {
                    IptvChannelInfo(
                        id = it.streamId,
                        name = it.name,
                        logoUrl = it.streamIcon,
                        streamUrl = config.liveStreamUrl(it.streamId),
                    )
                }
            is IptvSourceConfig.M3u ->
                m3uRemoteDataSource.fetchChannels(config.playlistUrl)
                    .filter { (it.groupTitle?.takeIf(String::isNotBlank) ?: UNCATEGORIZED_ID) == categoryId }
                    .map { IptvChannelInfo(id = it.id, name = it.name, logoUrl = it.logoUrl, streamUrl = it.streamUrl) }
        }
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
}
