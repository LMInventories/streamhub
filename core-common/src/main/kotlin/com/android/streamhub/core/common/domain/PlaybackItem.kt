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
    // A specific forced-flagged track's language the player should pin, deliberately kept separate
    // from preferredSubtitleLanguage: it's meant to survive subtitlesOff being true (a forced track
    // - e.g. the one foreign-language scene in an otherwise-English film - is meant to keep showing
    // even once the viewer turned regular subtitles off), and it needs the player to search for the
    // specific track carrying C.SELECTION_FLAG_FORCED once tracks load rather than just trusting
    // ExoPlayer's own language-based auto-selection, which can't disambiguate a forced and a full
    // track that happen to share the same language (common on e.g. anime releases).
    val forcedSubtitleLanguage: String? = null,
    // Enough to build/render trickplay scrubbing-preview thumbnails without the player needing to
    // know which source they came from - same "populated once at resolvePlayback() time, source-
    // agnostic from here on" pattern as liveProgramInfo above. Null for sources/items with no
    // trickplay data (IPTV/live, or Jellyfin content the server hasn't analyzed yet).
    val trickplay: TrickplayInfo? = null,
)

/**
 * [tileUrlTemplate] carries a literal "{index}" placeholder for the 0-based tile image index -
 * the player substitutes it per tile it actually needs while the user is scrubbing, rather than
 * every source having to expose its own URL-building logic to the player directly. [width]/
 * [height] are one individual thumbnail's pixel dimensions; [tileGridColumns]/[tileGridRows] are
 * how many thumbnails are packed into one tile image, together with [thumbnailCount] and
 * [intervalMs] enough to compute both which tile a playback position falls in and where within
 * it, with no network round trip needed just to look that up.
 */
data class TrickplayInfo(
    val tileUrlTemplate: String,
    val width: Int,
    val height: Int,
    val tileGridColumns: Int,
    val tileGridRows: Int,
    val thumbnailCount: Int,
    val intervalMs: Int,
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
