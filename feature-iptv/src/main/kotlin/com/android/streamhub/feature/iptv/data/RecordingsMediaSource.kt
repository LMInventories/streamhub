package com.android.streamhub.feature.iptv.data

import com.android.streamhub.core.common.domain.MediaSource
import com.android.streamhub.core.common.domain.PlaybackItem
import com.android.streamhub.core.common.domain.SourceType
import com.android.streamhub.feature.iptv.data.scheduled.RecordedItemEntity
import com.android.streamhub.feature.iptv.data.scheduled.ScheduledEventsRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Registers completed recordings into the same MediaSourceRegistry/player-screen seam every
 * other backend uses - id is just the Room row id as a string, since recordings have no external
 * catalog identifier the way IPTV/Jellyfin items do.
 */
@Singleton
class RecordingsMediaSource @Inject constructor(
    private val repository: ScheduledEventsRepository,
) : MediaSource {

    override val sourceType: SourceType = SourceType.RECORDING

    override suspend fun browse(): List<PlaybackItem> =
        repository.observeRecordedItems().first().map { it.toPlaybackItem() }

    // The file is already local and fully captured - there's no remote resolution/token refresh
    // step the way Xtream/Jellyfin need, so this is the same lookup as browse() filtered to one id.
    override suspend fun resolvePlayback(itemId: String): PlaybackItem {
        val id = itemId.toLongOrNull() ?: error("Invalid recording id: $itemId")
        val item = repository.observeRecordedItems().first().firstOrNull { it.id == id }
            ?: error("Recording not found: $itemId")
        return item.toPlaybackItem()
    }

    private fun RecordedItemEntity.toPlaybackItem(): PlaybackItem = PlaybackItem(
        id = id.toString(),
        sourceType = SourceType.RECORDING,
        title = programTitle,
        subtitle = channelName,
        posterUrl = channelLogoUrl,
        streamUri = "file://$filePath",
        mimeTypeHint = "video/mp2t",
        isLive = false,
    )
}
