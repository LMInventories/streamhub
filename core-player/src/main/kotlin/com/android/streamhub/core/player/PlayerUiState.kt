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
)
