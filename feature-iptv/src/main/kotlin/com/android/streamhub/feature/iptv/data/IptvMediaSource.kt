package com.android.streamhub.feature.iptv.data

import com.android.streamhub.core.common.domain.MediaSource
import com.android.streamhub.core.common.domain.PlaybackItem
import com.android.streamhub.core.common.domain.SourceType
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IptvMediaSource @Inject constructor(
    private val configRepository: IptvSourceConfigRepository,
    private val xtreamRemoteDataSource: XtreamRemoteDataSource,
    private val m3uRemoteDataSource: M3uRemoteDataSource,
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

    override suspend fun resolvePlayback(itemId: String): PlaybackItem =
        browse().first { it.id == itemId }
}

// Xtream live and VOD stream ids are separate numbering spaces on the provider side and can
// collide (e.g. both having a stream_id of "1") - browse() merges them into one flat list keyed
// by PlaybackItem.id, so VOD ids get this prefix to stay unambiguous. IptvVodRepository's
// VodMovieInfo.id must use the same prefix so Route.playerRoute(...) -> resolvePlayback(...)
// resolves the intended item rather than a same-numbered live channel.
internal fun vodPlaybackId(streamId: String): String = "vod:$streamId"

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

private fun M3uChannel.toPlaybackItem(): PlaybackItem = PlaybackItem(
    id = id,
    sourceType = SourceType.IPTV,
    title = name,
    subtitle = groupTitle,
    posterUrl = logoUrl,
    streamUri = streamUrl,
    isLive = true,
)
