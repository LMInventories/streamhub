package com.android.streamhub.core.player

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.android.streamhub.core.common.domain.PlaybackItem
import com.android.streamhub.core.player.download.PlaybackCacheDataSource
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
    @PlaybackCacheDataSource cacheDataSourceFactory: CacheDataSource.Factory,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var positionTicker: Job? = null

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState

    // EXTENSION_RENDERER_MODE_ON: prefer the platform's hardware decoder, but fall back to the
    // FFmpeg extension (org.jellyfin.media3:media3-ffmpeg-decoder, on the classpath below) for
    // codecs the device can't decode in hardware - AC3/EAC3 (Dolby) audio is common in IPTV/
    // satellite-sourced streams and isn't decoded by MediaCodec on most devices, which is why
    // some streams were previously silent instead of erroring.
    private val renderersFactory = DefaultRenderersFactory(context)
        .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)

    // Read-only cache data source (see PlaybackCacheDataSource's own doc) - a downloaded item
    // plays back straight from local disk through this same streamUri with no branching needed
    // anywhere else in this class; anything not downloaded falls straight through to the network
    // upstream exactly as before.
    private val mediaSourceFactory = DefaultMediaSourceFactory(context).setDataSourceFactory(cacheDataSourceFactory)

    val exoPlayer: ExoPlayer = ExoPlayer.Builder(context, renderersFactory)
        .setMediaSourceFactory(mediaSourceFactory)
        .build()

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
                    audioChannelCount = exoPlayer.audioFormat?.channelCount ?: 0,
                )
            }
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            _uiState.update {
                it.copy(
                    videoWidth = videoSize.width,
                    videoHeight = videoSize.height,
                    videoFrameRate = exoPlayer.videoFormat?.frameRate?.takeIf { fps -> fps > 0f } ?: 0f,
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

    /** For failures that happen before playback even starts (e.g. resolving the item over the network). */
    fun reportError(message: String) {
        _uiState.update { it.copy(errorMessage = message) }
    }

    fun prepare(item: PlaybackItem) {
        // Cleared up front, not left to whenever the next onVideoSizeChanged/onTracksChanged
        // callback happens to fire - otherwise switching to a new channel would keep showing the
        // previous one's stats badges for a moment.
        _uiState.update {
            it.copy(videoWidth = 0, videoHeight = 0, videoFrameRate = 0f, audioChannelCount = 0, errorMessage = null)
        }
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

        // Sets ExoPlayer's own preferred-language track selection parameters rather than manually
        // scanning onTracksChanged for a matching group - null clears the preference, which is
        // exactly "use the player's default selection", so this needs no branching for the
        // no-preference case either.
        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
            .setPreferredAudioLanguage(item.preferredAudioLanguage)
            .setPreferredTextLanguage(item.preferredSubtitleLanguage)
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

    fun setMuted(muted: Boolean) {
        exoPlayer.volume = if (muted) 0f else 1f
        _uiState.update { it.copy(isMuted = muted) }
    }

    fun toggleMuted() = setMuted(!uiState.value.isMuted)

    fun setAspectMode(mode: VideoAspectMode) {
        _uiState.update { it.copy(aspectMode = mode) }
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
