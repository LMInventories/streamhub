package com.android.streamhub.feature.iptv.data

import com.android.streamhub.core.common.domain.LiveProgramInfo
import com.android.streamhub.core.common.domain.MediaSource
import com.android.streamhub.core.common.domain.PlaybackItem
import com.android.streamhub.core.common.domain.SourceType
import com.android.streamhub.feature.iptv.data.recent.RecentChannelDao
import com.android.streamhub.feature.iptv.data.recent.RecentChannelEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IptvMediaSource @Inject constructor(
    private val configRepository: IptvSourceConfigRepository,
    private val xtreamRemoteDataSource: XtreamRemoteDataSource,
    private val m3uRemoteDataSource: M3uRemoteDataSource,
    private val browseRepository: IptvBrowseRepository,
    private val recentChannelDao: RecentChannelDao,
) : MediaSource {

    override val sourceType: SourceType = SourceType.IPTV

    // Config equality drives cache invalidation, so saving new settings and returning to Home
    // (same ViewModel/MediaSource instance, per @Singleton) picks up the change without needing
    // a separate "dirty" flag or a continuously-observed Flow.
    private var cachedConfig: IptvSourceConfig? = null
    private var cachedItems: List<PlaybackItem> = emptyList()

    override suspend fun browse(): List<PlaybackItem> {
        val config = configRepository.configFlow.first()
        if (config == cachedConfig) return cachedItems

        // Live channels and VOD movies are merged into one flat list here even though they're
        // browsed separately in the UI (LiveTv vs Vod screens) - this is the contract every
        // MediaSource.browse() caller (Master Search, resolvePlayback below) relies on to find
        // *any* playable item by id, not just live ones.
        val items = when (config) {
            is IptvSourceConfig.Xtream ->
                xtreamRemoteDataSource.getLiveStreams(config).map { it.toPlaybackItem(config) } +
                    runCatching { xtreamRemoteDataSource.getVodStreams(config) }.getOrDefault(emptyList())
                        .map { it.toPlaybackItem(config) }
            is IptvSourceConfig.M3u -> m3uRemoteDataSource.fetchChannels(config.playlistUrl).map { it.toPlaybackItem() }
            null -> emptyList()
        }
        cachedConfig = config
        cachedItems = items
        return items
    }

    override suspend fun resolvePlayback(itemId: String): PlaybackItem {
        // Episodes deliberately aren't part of browse()'s cached flat list - eagerly fetching
        // every series' full episode list just to populate that cache would mean an N+1 burst of
        // get_series_info calls on every app launch. Resolved lazily instead, on demand, exactly
        // like a fresh Retrofit call for the one series actually being played from.
        val episodeId = parseEpisodePlaybackId(itemId)
        if (episodeId != null) {
            val (seriesId, rawEpisodeId) = episodeId
            val config = configRepository.configFlow.first() as? IptvSourceConfig.Xtream
                ?: error("Episode playback requires an Xtream source")
            val response = xtreamRemoteDataSource.getSeriesInfo(config, seriesId)
            val episode = response.episodes.values.flatten().first { it.id == rawEpisodeId }
            return episode.toPlaybackItem(config, itemId)
        }
        val item = browse().first { it.id == itemId }
        if (!item.isLive) return item

        // Enriched here rather than in browse()'s flat list - fetching now/next EPG for every
        // channel just to populate a list the user might not even open would mean an EPG call
        // per channel on every app launch. This runs exactly once, for the specific channel
        // actually being played.
        val (now, next) = runCatching { browseRepository.getNowNext(itemId) }.getOrDefault(null to null)
        return item.copy(
            liveProgramInfo = LiveProgramInfo(
                channelName = item.title,
                channelLogoUrl = item.posterUrl,
                nowTitle = now?.title,
                nowStartAtEpochMs = now?.startAt?.toEpochMilli(),
                nowEndAtEpochMs = now?.endAt?.toEpochMilli(),
                nextTitle = next?.title,
                nextStartAtEpochMs = next?.startAt?.toEpochMilli(),
            ),
        )
    }

    override suspend fun recordViewed(itemId: String) {
        val item = runCatching { browse().first { it.id == itemId } }.getOrNull() ?: return
        if (!item.isLive) return
        recentChannelDao.upsert(
            RecentChannelEntity(
                channelId = item.id,
                name = item.title,
                logoUrl = item.posterUrl,
                streamUrl = item.streamUri,
                lastViewedAtEpochSeconds = Instant.now().epochSecond,
            ),
        )
        recentChannelDao.trimToLimit()
    }

    override fun observeRecentlyViewed(): Flow<List<PlaybackItem>> =
        recentChannelDao.observeRecent().map { entities ->
            entities.map {
                PlaybackItem(
                    id = it.channelId,
                    sourceType = SourceType.IPTV,
                    title = it.name,
                    posterUrl = it.logoUrl,
                    streamUri = it.streamUrl,
                    isLive = true,
                )
            }
        }

    /** Forces the next browse() to refetch instead of returning the cached list - used by "Update Playlist" in Settings. */
    fun invalidateCache() {
        cachedConfig = null
    }
}

// Xtream live and VOD stream ids are separate numbering spaces on the provider side and can
// collide (e.g. both having a stream_id of "1") - browse() merges them into one flat list keyed
// by PlaybackItem.id, so VOD ids get this prefix to stay unambiguous. IptvVodRepository's
// VodMovieInfo.id must use the same prefix so Route.playerRoute(...) -> resolvePlayback(...)
// resolves the intended item rather than a same-numbered live channel.
internal fun vodPlaybackId(streamId: String): String = "vod:$streamId"

internal fun episodePlaybackId(seriesId: String, episodeId: String): String = "episode:$seriesId:$episodeId"

/** Returns (seriesId, episodeId) if [playbackId] is an episode id, null otherwise (movie/live channel ids never contain this prefix). */
internal fun parseEpisodePlaybackId(playbackId: String): Pair<String, String>? {
    if (!playbackId.startsWith("episode:")) return null
    val parts = playbackId.removePrefix("episode:").split(":", limit = 2)
    if (parts.size != 2) return null
    return parts[0] to parts[1]
}

private fun XtreamLiveStream.toPlaybackItem(config: IptvSourceConfig.Xtream): PlaybackItem = PlaybackItem(
    id = streamId,
    sourceType = SourceType.IPTV,
    title = name,
    posterUrl = streamIcon,
    streamUri = config.liveStreamUrl(streamId),
    isLive = true,
)

private fun XtreamVodStream.toPlaybackItem(config: IptvSourceConfig.Xtream): PlaybackItem = PlaybackItem(
    id = vodPlaybackId(streamId),
    sourceType = SourceType.IPTV,
    title = name,
    posterUrl = streamIcon,
    streamUri = config.vodStreamUrl(streamId, containerExtension ?: "mp4"),
    isLive = false,
)

private fun XtreamEpisode.toPlaybackItem(config: IptvSourceConfig.Xtream, playbackId: String): PlaybackItem = PlaybackItem(
    id = playbackId,
    sourceType = SourceType.IPTV,
    title = title?.takeIf(String::isNotBlank) ?: "Episode",
    posterUrl = info?.movieImage,
    streamUri = config.vodStreamUrl(id, containerExtension ?: "mp4"),
    isLive = false,
)

private fun M3uChannel.toPlaybackItem(): PlaybackItem = PlaybackItem(
    id = id,
    sourceType = SourceType.IPTV,
    title = name,
    subtitle = groupTitle,
    posterUrl = logoUrl,
    streamUri = streamUrl,
    isLive = true,
)
