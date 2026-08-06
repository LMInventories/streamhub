package com.android.streamhub.core.player

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
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

    // Consumed by the very next onTracksChanged after prepare() (one-shot, cleared as soon as it's
    // acted on) - group/track indices for a forced track aren't known until tracks actually load, so
    // this carries the *language* to search for across that gap. forcedSubtitleFallbackToOff mirrors
    // the item's own subtitlesOff at prepare() time: if no forced track is actually found once
    // tracks load, that's when Off gets genuinely enforced (see onTracksChanged).
    private var pendingForcedSubtitleLanguage: String? = null
    private var forcedSubtitleFallbackToOff: Boolean = false

    // Durable (not one-shot) for the currently prepared item - unlike pendingForcedSubtitleLanguage
    // above, this survives past the initial post-prepare search so clearTextTrack() (the in-player
    // "Off" tap, mid-playback) can also re-pin the forced track instead of hard-disabling it, the
    // same "Off means no regular subtitles, forced still shows" rule the initial load applies.
    private var currentForcedSubtitleLanguage: String? = null

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
            pendingForcedSubtitleLanguage?.let { language ->
                pendingForcedSubtitleLanguage = null
                val applied = applyForcedTextOverride(tracks, language)
                if (!applied && forcedSubtitleFallbackToOff) {
                    exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                        .build()
                }
            }
            val subtitlesOff = exoPlayer.trackSelectionParameters
                .disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
            val videoFormat = exoPlayer.videoFormat
            val audioFormat = exoPlayer.audioFormat
            _uiState.update {
                it.copy(
                    audioTracks = tracks.toTrackOptions(C.TRACK_TYPE_AUDIO),
                    subtitleTracks = tracks.toTrackOptions(C.TRACK_TYPE_TEXT),
                    subtitlesOff = subtitlesOff,
                    audioChannelCount = audioFormat?.channelCount ?: 0,
                    videoCodecMimeType = videoFormat?.sampleMimeType,
                    videoBitrateBps = videoFormat?.bitrate?.takeIf { bitrate -> bitrate != Format.NO_VALUE } ?: 0,
                    audioCodecMimeType = audioFormat?.sampleMimeType,
                    audioBitrateBps = audioFormat?.bitrate?.takeIf { bitrate -> bitrate != Format.NO_VALUE } ?: 0,
                    hdrType = hdrLabel(videoFormat),
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
            it.copy(
                videoWidth = 0,
                videoHeight = 0,
                videoFrameRate = 0f,
                audioChannelCount = 0,
                videoCodecMimeType = null,
                videoBitrateBps = 0,
                audioCodecMimeType = null,
                audioBitrateBps = 0,
                hdrType = null,
                errorMessage = null,
            )
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

        // A forced track (item.forcedSubtitleLanguage) is searched for precisely once tracks load
        // (see onTracksChanged/applyForcedTextOverride) rather than trusted to setPreferredTextLanguage
        // below - a forced and a full track can share the same language, which plain language
        // preference can't disambiguate. Its presence also keeps TEXT enabled up front even when
        // subtitlesOff is true, since a forced track is meant to survive an explicit Off (see
        // PlaybackItem.forcedSubtitleLanguage's own doc) - onTracksChanged falls back to a genuine
        // hard-disable if the search comes up empty.
        pendingForcedSubtitleLanguage = item.forcedSubtitleLanguage
        forcedSubtitleFallbackToOff = item.subtitlesOff
        currentForcedSubtitleLanguage = item.forcedSubtitleLanguage

        // Sets ExoPlayer's own preferred-language track selection parameters rather than manually
        // scanning onTracksChanged for a matching group - null clears the preference, which is
        // exactly "use the player's default selection", so this needs no branching for the
        // no-preference case either. setTrackTypeDisabled/clearOverridesOfType are explicitly set
        // (not just left alone) on every prepare() call - without that, an explicit Off or track
        // override chosen for a previous item in this same PlayerController instance (e.g. via
        // clearTextTrack()/selectTextTrack()) would otherwise silently carry over to a new item that
        // never asked for that.
        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
            .setPreferredAudioLanguage(item.preferredAudioLanguage)
            .setPreferredTextLanguage(item.preferredSubtitleLanguage)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, item.subtitlesOff && item.forcedSubtitleLanguage == null)
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
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

    /** Not reset in prepare() - like aspectMode, a chosen speed carries across a chained Next Episode load within the same player screen/PlayerController instance, which is what a viewer bingeing at e.g. 1.5x actually wants. */
    fun setPlaybackSpeed(speed: Float) {
        exoPlayer.playbackParameters = PlaybackParameters(speed)
        _uiState.update { it.copy(playbackSpeed = speed) }
    }

    fun selectAudioTrack(trackId: String) = selectTrack(C.TRACK_TYPE_AUDIO, trackId)

    fun selectTextTrack(trackId: String) {
        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .build()
        selectTrack(C.TRACK_TYPE_TEXT, trackId)
    }

    fun clearTextTrack() {
        val forced = currentForcedSubtitleLanguage
        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .build()
        // Re-pin the forced track (if this item has one) straight after disabling everything else -
        // "Off" means no regular subtitles, not "ignore dialogue the viewer can't otherwise follow".
        if (forced != null) applyForcedTextOverride(exoPlayer.currentTracks, forced)
    }

    /**
     * Finds the track in [language] flagged C.SELECTION_FLAG_FORCED and pins it via the same
     * override mechanism as selectTrack, preferring it over any same-language non-forced track
     * (e.g. a "Full" track sharing a language with a "Forced/Signs" one). Falls back to the only
     * same-language candidate when none carries the flag - some containers/transcodes don't
     * preserve it, so the language match alone is still the best available signal. Returns false
     * (nothing applied) when there's no candidate in that language at all.
     */
    private fun applyForcedTextOverride(tracks: Tracks, language: String): Boolean {
        val candidates = tracks.groups.withIndex()
            .filter { (_, group) -> group.type == C.TRACK_TYPE_TEXT }
            .flatMap { (groupIndex, group) ->
                (0 until group.length).map { trackIndexInGroup -> Triple(groupIndex, trackIndexInGroup, group.getTrackFormat(trackIndexInGroup)) }
            }
            .filter { (_, _, format) -> language.equals(format.language, ignoreCase = true) }
        val (groupIndex, trackIndexInGroup, _) = candidates.firstOrNull { (_, _, format) ->
            format.selectionFlags and C.SELECTION_FLAG_FORCED != 0
        } ?: candidates.firstOrNull() ?: return false

        val group = tracks.groups[groupIndex]
        val override = TrackSelectionOverride(group.mediaTrackGroup, listOf(trackIndexInGroup))
        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .setOverrideForType(override)
            .build()
        return true
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

    /**
     * Dolby Vision is its own MIME type (a distinct elementary stream, not just a color-metadata
     * flag on an HEVC/AV1 one) - checked first since a DV stream also reports an ST2084 transfer
     * for HDR10-fallback compatibility, which would otherwise misreport it as plain HDR10. HLG (used
     * by some broadcast/IPTV sources) doesn't use PQ transfer at all, so it needs its own branch.
     */
    private fun hdrLabel(format: Format?): String? {
        if (format == null) return null
        if (format.sampleMimeType == MimeTypes.VIDEO_DOLBY_VISION) return "Dolby Vision"
        return when (format.colorInfo?.colorTransfer) {
            C.COLOR_TRANSFER_ST2084 -> "HDR10"
            C.COLOR_TRANSFER_HLG -> "HLG"
            else -> null
        }
    }
}
