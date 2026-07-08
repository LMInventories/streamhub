package com.android.streamhub.core.common.domain

/**
 * A subtitle track not embedded in the media container (e.g. a sidecar SRT/VTT file),
 * so the player needs to be told about it explicitly rather than discovering it from the stream.
 */
data class SubtitleTrackRef(
    val uri: String,
    val language: String?,
    val label: String,
    val mimeType: String,
)

/**
 * A single playable unit from any source (IPTV channel/VOD title, Jellyfin/Emby library item).
 * [id] is only unique within [sourceType] - callers must key by (sourceType, id).
 */
data class PlaybackItem(
    val id: String,
    val sourceType: SourceType,
    val title: String,
    val subtitle: String? = null,
    val posterUrl: String? = null,
    val streamUri: String,
    val mimeTypeHint: String? = null,
    val subtitleTracks: List<SubtitleTrackRef> = emptyList(),
    val startPositionMs: Long = 0L,
    val isLive: Boolean = false,
    // Only populated for live-TV channel items - null for VOD/Jellyfin/Emby. Kept on PlaybackItem
    // rather than a separate lookup so the player screen (source-agnostic, doesn't depend on
    // feature-iptv) can render the EPG overlay from whatever MediaSource.resolvePlayback()
    // already handed it, without needing an IPTV-specific call of its own.
    val liveProgramInfo: LiveProgramInfo? = null,
)

data class LiveProgramInfo(
    val channelName: String,
    val channelLogoUrl: String?,
    val nowTitle: String?,
    val nowStartAtEpochMs: Long?,
    val nowEndAtEpochMs: Long?,
    val nextTitle: String?,
    val nextStartAtEpochMs: Long?,
)
