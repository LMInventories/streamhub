package com.android.streamhub.feature.iptv.data.recent

import com.android.streamhub.feature.iptv.data.IptvChannelInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * IPTV-native wrapper around RecentChannelDao - shared by IptvMediaSource (which maps to/from
 * the cross-source PlaybackItem for MediaSource.recordViewed/observeRecentlyViewed) and
 * LiveTvViewModel directly (which works in IptvChannelInfo throughout and has no reason to go
 * through the cross-source MediaSource abstraction for its own in-place fullscreen overlay).
 */
@Singleton
class RecentChannelsRepository @Inject constructor(
    private val dao: RecentChannelDao,
) {
    fun observeRecent(): Flow<List<IptvChannelInfo>> =
        dao.observeRecent().map { entities -> entities.map { it.toIptvChannelInfo() } }

    suspend fun recordViewed(channel: IptvChannelInfo) {
        dao.upsert(
            RecentChannelEntity(
                channelId = channel.id,
                name = channel.name,
                logoUrl = channel.logoUrl,
                streamUrl = channel.streamUrl,
                lastViewedAtEpochSeconds = Instant.now().epochSecond,
            ),
        )
        dao.trimToLimit()
    }
}

private fun RecentChannelEntity.toIptvChannelInfo() = IptvChannelInfo(id = channelId, name = name, logoUrl = logoUrl, streamUrl = streamUrl)
