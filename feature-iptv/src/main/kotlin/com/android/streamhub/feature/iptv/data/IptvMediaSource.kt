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

        val items = when (config) {
            is IptvSourceConfig.Xtream -> xtreamRemoteDataSource.getLiveStreams(config).map { it.toPlaybackItem(config) }
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

private fun XtreamLiveStream.toPlaybackItem(config: IptvSourceConfig.Xtream): PlaybackItem = PlaybackItem(
    id = streamId,
    sourceType = SourceType.IPTV,
    title = name,
    posterUrl = streamIcon,
    streamUri = config.liveStreamUrl(streamId),
    isLive = true,
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
