package com.android.streamhub.feature.jellyfin.data

import com.android.streamhub.core.common.domain.MediaSource
import com.android.streamhub.core.common.domain.PlaybackItem
import com.android.streamhub.core.common.domain.SourceType
import javax.inject.Inject
import javax.inject.Singleton

// Ticks are Jellyfin's .NET-derived 100ns unit throughout its API - 10_000 ticks/ms.
private const val TICKS_PER_MS = 10_000L

/**
 * Cross-source adapter (Master Search/Favorites, once those exist) over JellyfinBrowseRepository.
 * The dedicated home/library/detail screens in this module talk to JellyfinBrowseRepository
 * directly instead of through here - PlaybackItem is a deliberately flattened, source-agnostic
 * shape that would lose the season/episode/cast structure those screens need.
 */
@Singleton
class JellyfinMediaSource @Inject constructor(
    private val browseRepository: JellyfinBrowseRepository,
) : MediaSource {

    override val sourceType: SourceType = SourceType.JELLYFIN

    override suspend fun browse(): List<PlaybackItem> {
        // A single reasonably-large page per library rather than looping every page - nothing
        // depends on this being a fully exhaustive catalog yet (Master Search doesn't exist
        // in this app), and resolvePlayback() below looks items up directly by id rather than
        // scanning this list, unlike feature-iptv's Xtream adapter.
        return browseRepository.getLibraries().flatMap { library ->
            val itemType = when (library.type) {
                JellyfinLibraryType.MOVIES -> JellyfinItemType.MOVIE
                JellyfinLibraryType.TV_SHOWS -> JellyfinItemType.SERIES
            }
            browseRepository.getItems(library.id, itemType, startIndex = 0, limit = 500)
        }.map { it.toPlaybackItem(streamUri = "") }
    }

    override suspend fun resolvePlayback(itemId: String): PlaybackItem {
        val item = browseRepository.getItem(itemId) ?: error("Jellyfin item not found: $itemId")
        val streamUrl = browseRepository.getStreamUrl(itemId) ?: error("No Jellyfin stream URL for: $itemId")
        return item.toPlaybackItem(streamUrl)
    }

    private fun JellyfinItemInfo.toPlaybackItem(streamUri: String): PlaybackItem = PlaybackItem(
        id = id,
        sourceType = SourceType.JELLYFIN,
        title = name,
        subtitle = seriesName,
        posterUrl = primaryImageUrl,
        streamUri = streamUri,
        startPositionMs = resumePositionTicks / TICKS_PER_MS,
        isLive = false,
    )
}
