package com.android.streamhub.core.player

data class PlayerUiState(
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val audioTracks: List<TrackOption> = emptyList(),
    val subtitleTracks: List<TrackOption> = emptyList(),
    val subtitlesOff: Boolean = true,
    val isMuted: Boolean = false,
    val aspectMode: VideoAspectMode = VideoAspectMode.FIT,
    val playbackSpeed: Float = 1f,
    val errorMessage: String? = null,
    // Raw stream stats from the active decoded format - the UI layer turns these into badges
    // (e.g. "1080p"/"FHD", "25 FPS", "5.1"), this layer just reports what ExoPlayer reports.
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    val videoFrameRate: Float = 0f,
    val audioChannelCount: Int = 0,
    // Stats-for-nerds panel fields - raw MIME subtype/bps, same "layer just reports what ExoPlayer
    // reports" contract as the fields above; codecLabel()/bitrateLabel() in PlayerFormatting.kt
    // turn these into display strings. hdrType is already resolved to a friendly label ("HDR10",
    // "Dolby Vision", "HLG") or null for SDR/unknown - unlike the others, there's no raw form of
    // this worth exposing separately since it's derived from two different signals (colorTransfer
    // and the Dolby Vision MIME type) that only make sense combined.
    val videoCodecMimeType: String? = null,
    val videoBitrateBps: Int = 0,
    val audioCodecMimeType: String? = null,
    val audioBitrateBps: Int = 0,
    val hdrType: String? = null,
)
