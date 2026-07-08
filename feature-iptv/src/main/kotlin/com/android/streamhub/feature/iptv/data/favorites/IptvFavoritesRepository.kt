package com.android.streamhub.feature.iptv.data.favorites

import com.android.streamhub.feature.iptv.data.IptvChannelInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reactive on purpose (Flow all the way from Room, not one-shot suspend reads) - the category
 * list's pinned Favourites entry and every channel row's long-press menu need to update
 * immediately when a favourite is added/removed anywhere, without a manual refresh.
 */
@Singleton
class IptvFavoritesRepository @Inject constructor(
    private val dao: FavoriteChannelDao,
) {
    fun observeFavorites(): Flow<List<IptvChannelInfo>> =
        dao.observeAll().map { entities ->
            entities.map { IptvChannelInfo(id = it.channelId, name = it.name, logoUrl = it.logoUrl, streamUrl = it.streamUrl) }
        }

    fun observeFavoriteIds(): Flow<Set<String>> =
        dao.observeFavoriteIds().map { it.toSet() }

    suspend fun addFavorite(channel: IptvChannelInfo) {
        dao.add(
            FavoriteChannelEntity(
                channelId = channel.id,
                name = channel.name,
                logoUrl = channel.logoUrl,
                streamUrl = channel.streamUrl,
                addedAtEpochSeconds = Instant.now().epochSecond,
            ),
        )
    }

    suspend fun removeFavorite(channelId: String) {
        dao.remove(channelId)
    }
}
