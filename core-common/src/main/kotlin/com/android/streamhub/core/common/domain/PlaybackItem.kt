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
    // ISO 639-2 language codes (e.g. "eng") - a generic hint any source can populate from its own
    // per-source settings (only Jellyfin does today) rather than the player needing to know which
    // source a track preference came from. Null means "no preference, use the player's default
    // track selection". core-player applies these directly as ExoPlayer's own preferred-language
    // track selection parameters, so there's no manual track-scanning logic to keep in sync here.
    val preferredAudioLanguage: String? = null,
    val preferredSubtitleLanguage: String? = null,
    // Explicit "start with subtitles off" override - distinct from preferredSubtitleLanguage
    // being null, which just means "no language preference" and still lets the player's default
    // track selector auto-pick a forced/default-flagged embedded track. This is a hard disable,
    // for the case where a source's own per-item picker was explicitly set to Off.
    val subtitlesOff: Boolean = false,
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
