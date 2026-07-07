package com.android.streamhub.core.player

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.android.streamhub.core.common.domain.PlaybackItem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Owns one ExoPlayer instance for the lifetime of a single player screen. Not a singleton -
 * inject fresh into each PlayerViewModel and call [release] when that screen is torn down.
 */
@UnstableApi
class PlayerController @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var positionTicker: Job? = null

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState

    val exoPlayer: ExoPlayer = ExoPlayer.Builder(context).build()

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _uiState.update { it.copy(isPlaying = isPlaying) }
            if (isPlaying) startPositionTicker() else stopPositionTicker()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            _uiState.update {
                it.copy(
                    isBuffering = playbackState == Player.STATE_BUFFERING,
                    durationMs = exoPlayer.duration.coerceAtLeast(0L),
                )
            }
        }

        override fun onTracksChanged(tracks: Tracks) {
            val subtitlesOff = exoPlayer.trackSelectionParameters
                .disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
            _uiState.update {
                it.copy(
                    audioTracks = tracks.toTrackOptions(C.TRACK_TYPE_AUDIO),
                    subtitleTracks = tracks.toTrackOptions(C.TRACK_TYPE_TEXT),
                    subtitlesOff = subtitlesOff,
                )
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            _uiState.update { it.copy(errorMessage = error.message ?: "Playback error") }
        }
    }

    init {
        exoPlayer.addListener(playerListener)
    }

    fun prepare(item: PlaybackItem) {
        val mediaItem = MediaItem.Builder()
            .setUri(item.streamUri)
            .apply { item.mimeTypeHint?.let { setMimeType(it) } }
            .setSubtitleConfigurations(
                item.subtitleTracks.map { track ->
                    MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(track.uri))
                        .setMimeType(track.mimeType)
                        .setLanguage(track.language)
                        .setLabel(track.label)
                        .build()
                },
            )
            .build()

        exoPlayer.setMediaItem(mediaItem, item.startPositionMs)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    fun play() {
        exoPlayer.play()
    }

    fun pause() {
        exoPlayer.pause()
    }

    fun seekTo(positionMs: Long) {
        exoPlayer.seekTo(positionMs)
    }

    fun selectAudioTrack(trackId: String) = selectTrack(C.TRACK_TYPE_AUDIO, trackId)

    fun selectTextTrack(trackId: String) {
        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .build()
        selectTrack(C.TRACK_TYPE_TEXT, trackId)
    }

    fun clearTextTrack() {
        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .build()
    }

    private fun selectTrack(trackType: Int, trackId: String) {
        val parts = trackId.split(":")
        val groupIndex = parts[0].toInt()
        val trackIndexInGroup = parts[1].toInt()
        val group = exoPlayer.currentTracks.groups.getOrNull(groupIndex) ?: return
        val override = TrackSelectionOverride(group.mediaTrackGroup, listOf(trackIndexInGroup))
        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
            .setOverrideForType(override)
            .build()
    }

    private fun startPositionTicker() {
        if (positionTicker?.isActive == true) return
        positionTicker = scope.launch {
            while (true) {
                _uiState.update {
                    it.copy(
                        positionMs = exoPlayer.currentPosition.coerceAtLeast(0L),
                        durationMs = exoPlayer.duration.coerceAtLeast(0L),
                    )
                }
                delay(500)
            }
        }
    }

    private fun stopPositionTicker() {
        positionTicker?.cancel()
        positionTicker = null
    }

    fun release() {
        stopPositionTicker()
        exoPlayer.removeListener(playerListener)
        exoPlayer.release()
    }
}
