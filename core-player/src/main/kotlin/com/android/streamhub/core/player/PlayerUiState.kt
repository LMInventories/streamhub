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
    val errorMessage: String? = null,
    // Raw stream stats from the active decoded format - the UI layer turns these into badges
    // (e.g. "1080p"/"FHD", "25 FPS", "5.1"), this layer just reports what ExoPlayer reports.
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    val videoFrameRate: Float = 0f,
    val audioChannelCount: Int = 0,
)
