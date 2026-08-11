package com.android.streamhub.feature.player

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.MaterialTheme as MaterialTheme3
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.android.streamhub.core.common.domain.LiveProgramInfo
import com.android.streamhub.core.common.domain.PlaybackItem
import com.android.streamhub.core.common.domain.TrickplayInfo
import com.android.streamhub.core.design.Palette
import com.android.streamhub.core.design.SignalBar
import com.android.streamhub.core.design.tvFocusBorder
import com.android.streamhub.core.player.PlayerUiState
import com.android.streamhub.core.player.TrackOption
import com.android.streamhub.core.player.VideoAspectMode
import com.android.streamhub.core.player.VideoSurface
import com.android.streamhub.core.player.audioChannelsLabel
import com.android.streamhub.core.player.aspectRatioLabel
import com.android.streamhub.core.player.bitrateLabel
import com.android.streamhub.core.player.codecLabel
import com.android.streamhub.core.player.formatPositionMs
import com.android.streamhub.core.player.frameRateLabel
import com.android.streamhub.core.player.KeepScreenOnWhileOpen
import com.android.streamhub.core.player.PLAYBACK_SPEED_OPTIONS
import com.android.streamhub.core.player.playbackSpeedLabel
import com.android.streamhub.core.player.resolutionLabel
import com.android.streamhub.core.ui.phone.theme.appColorScheme
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

private const val SEEK_STEP_MS = 10_000L

/** HIDDEN: nothing on screen but the video itself - D-pad Up/Down/Left/Right/OK act as direct
 * shortcuts (see the key handler below). CONTROLS: the full transport bar, back button and seek
 * bar - normal D-pad focus navigation between buttons takes over, same as any other screen.
 * INFO: a lightweight glance panel (title/stream stats), dismissed the same way it's shown. */
private enum class TvOverlayMode { HIDDEN, CONTROLS, INFO }

@Composable
fun PlayerScreenTv(
    onBack: () -> Unit,
    matchRefreshRate: Boolean = false,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentItem by viewModel.currentItem.collectAsStateWithLifecycle()
    val recentChannels by viewModel.recentChannels.collectAsStateWithLifecycle()
    val nextItem by viewModel.nextItem.collectAsStateWithLifecycle()
    val creditsStartMs by viewModel.creditsStartMs.collectAsStateWithLifecycle()
    val introRange by viewModel.introRange.collectAsStateWithLifecycle()

    KeepScreenOnWhileOpen()
    MatchRefreshRateWhilePlaying(enabled = matchRefreshRate, contentFrameRate = uiState.videoFrameRate)

    // Visible only while actually inside the analyzed intro window - see MediaSource.resolvePlaybackSegments.
    val skipIntroVisible = introRange?.let { uiState.positionMs in it } == true && currentItem?.isLive == false
    val skipIntroFocusRequester = remember { FocusRequester() }
    LaunchedEffect(skipIntroVisible) {
        if (skipIntroVisible) runCatching { skipIntroFocusRequester.requestFocus() }
    }

    // Non-null once the episode's real credits start (if known) or, failing that, in the last ten
    // seconds of an episode that actually has a follow-up - see nextEpisodeCountdownSeconds. Live
    // content never qualifies (no meaningful duration).
    val nextEpisodeCountdown = nextItem
        ?.takeIf { currentItem?.isLive == false }
        ?.let { nextEpisodeCountdownSeconds(uiState.positionMs, uiState.durationMs, creditsStartMs) }
    val nextEpisodePromptVisible = nextEpisodeCountdown != null

    LaunchedEffect(nextEpisodeCountdown) {
        if (nextEpisodeCountdown == 0) viewModel.playNextItem()
    }

    val watchNowFocusRequester = remember { FocusRequester() }
    // Same requestFocus-on-appear reasoning as the controls below, and the same runCatching guard
    // against the button not being attached on the exact frame this effect runs.
    LaunchedEffect(nextEpisodePromptVisible) {
        if (nextEpisodePromptVisible) runCatching { watchNowFocusRequester.requestFocus() }
    }

    var overlayMode by remember { mutableStateOf(TvOverlayMode.CONTROLS) }
    var showAudioPicker by remember { mutableStateOf(false) }
    var showSubtitlePicker by remember { mutableStateOf(false) }
    var showAspectPicker by remember { mutableStateOf(false) }
    var showSpeedPicker by remember { mutableStateOf(false) }
    // Defaults to Play/Pause (not Back) - Back used to be the button that grabbed initial focus,
    // so the very first OK press on entering this screen (a completely natural first thing to
    // try) closed playback instead of doing anything play-related. Play/Pause is both the most
    // expected default target and harmless to hit by accident.
    val playPauseFocusRequester = remember { FocusRequester() }

    val onPlayPause: () -> Unit = { overlayMode = TvOverlayMode.CONTROLS; viewModel.togglePlayPause() }
    val onSeekBack: () -> Unit = { viewModel.seekTo((uiState.positionMs - SEEK_STEP_MS).coerceAtLeast(0L)) }
    val onSeekForward: () -> Unit = { viewModel.seekTo((uiState.positionMs + SEEK_STEP_MS).coerceAtMost(uiState.durationMs)) }

    // Back closes the overlay, not the player, whenever there's an overlay up to close - only
    // falls through to onBack (leaving playback) once we're already HIDDEN. Disabled rather than
    // omitted so the popBackStack from onBack doesn't fire on the same press that dismisses INFO
    // or CONTROLS.
    BackHandler(enabled = overlayMode != TvOverlayMode.HIDDEN) {
        overlayMode = TvOverlayMode.HIDDEN
    }

    // Bumped on every key press while an overlay is up, and included below as a LaunchedEffect
    // key - reassigning overlayMode to the value it already holds (e.g. pressing Play/Pause again
    // while CONTROLS is showing) doesn't recompose on its own since Compose state skips
    // no-op writes, so without this the hide countdown would keep ticking down from when the
    // overlay first appeared instead of resetting on continued use.
    var interactionTick by remember { mutableIntStateOf(0) }

    LaunchedEffect(overlayMode, uiState.isPlaying, interactionTick) {
        if (overlayMode != TvOverlayMode.HIDDEN && uiState.isPlaying) {
            delay(3000)
            overlayMode = TvOverlayMode.HIDDEN
        }
    }

    // AnimatedVisibility tears the controls' whole focus tree down and rebuilds it every time
    // they auto-hide/reappear - Compose's focus system doesn't automatically pick a new focused
    // node when that happens, so without this, D-pad input goes nowhere at all once the controls
    // come back until something else claims focus, which reads as the remote being stuck.
    // runCatching guards a real race: this effect and AnimatedVisibility mounting the button it
    // targets are both triggered by the same overlayMode flip, with no guaranteed ordering between
    // them - if the effect runs first, requestFocus() throws IllegalStateException (target not
    // attached yet) and, uncaught, would crash the whole player rather than just skip a focus
    // request that'll succeed next time.
    LaunchedEffect(overlayMode) {
        if (overlayMode == TvOverlayMode.CONTROLS) runCatching { playPauseFocusRequester.requestFocus() }
    }

    // Same "OK/Down brings hidden TV controls back" fix as LiveFullscreenOverlay (this screen's
    // own equivalent gap - it's what every VOD/Jellyfin/downloaded item actually plays through).
    // Once controls auto-hide, every focusable button in them is removed from composition -
    // without a focusable node behind them to catch the key event, a D-pad press had nothing to
    // land on at all. Up/Left/Right/Down/OK are only intercepted while NOT showing the full
    // controls - once CONTROLS is up, normal D-pad focus navigation between its buttons takes
    // over instead, so these don't fight with e.g. using Left/Right to move between buttons in
    // the row. MediaPlayPause is the one binding that always works regardless of mode, matching a
    // physical remote's dedicated play/pause key.
    val boxFocusRequester = remember { FocusRequester() }
    LaunchedEffect(overlayMode) {
        if (overlayMode == TvOverlayMode.HIDDEN) runCatching { boxFocusRequester.requestFocus() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(boxFocusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                // While the Next Episode prompt is up it owns the D-pad - without this the
                // shortcuts below would swallow Left/Right/OK before its own buttons ever saw
                // them, leaving the prompt visible but impossible to actually answer.
                if (nextEpisodePromptVisible) return@onPreviewKeyEvent false
                // Same reasoning as the Next Episode prompt above - while Skip Intro is showing,
                // its own button owns the D-pad rather than these shortcuts swallowing OK before it.
                if (skipIntroVisible) return@onPreviewKeyEvent false
                if (overlayMode != TvOverlayMode.HIDDEN) interactionTick++
                when (event.key) {
                    Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause -> {
                        onPlayPause()
                        true
                    }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        if (overlayMode != TvOverlayMode.CONTROLS) {
                            overlayMode = TvOverlayMode.CONTROLS
                            true
                        } else {
                            false
                        }
                    }
                    Key.DirectionDown -> {
                        if (overlayMode != TvOverlayMode.CONTROLS) {
                            overlayMode = TvOverlayMode.CONTROLS
                            true
                        } else {
                            false
                        }
                    }
                    Key.DirectionUp -> {
                        if (overlayMode != TvOverlayMode.CONTROLS) {
                            overlayMode = TvOverlayMode.INFO
                            true
                        } else {
                            false
                        }
                    }
                    Key.DirectionLeft -> {
                        if (overlayMode != TvOverlayMode.CONTROLS) {
                            onSeekBack()
                            true
                        } else {
                            false
                        }
                    }
                    Key.DirectionRight -> {
                        if (overlayMode != TvOverlayMode.CONTROLS) {
                            onSeekForward()
                            true
                        } else {
                            false
                        }
                    }
                    else -> false
                }
            },
    ) {
        VideoSurface(
            exoPlayer = viewModel.exoPlayer,
            aspectMode = uiState.aspectMode,
            modifier = Modifier.fillMaxSize(),
        )

        if (uiState.isBuffering) {
            CircularProgressIndicator(color = Palette.Accent, modifier = Modifier.align(Alignment.Center))
        }

        AnimatedVisibility(visible = overlayMode == TvOverlayMode.INFO, enter = fadeIn(), exit = fadeOut()) {
            TvMediaInfoPanel(uiState = uiState, currentItem = currentItem)
        }

        AnimatedVisibility(visible = overlayMode == TvOverlayMode.CONTROLS, enter = fadeIn(), exit = fadeOut()) {
            TvPlayerControls(
                uiState = uiState,
                currentItem = currentItem,
                recentChannels = recentChannels,
                playPauseFocusRequester = playPauseFocusRequester,
                onBack = onBack,
                onPlayPause = onPlayPause,
                onSeekBack = onSeekBack,
                onSeekForward = onSeekForward,
                onSeek = viewModel::seekTo,
                onAudioTrackClick = { showAudioPicker = true },
                onSubtitleTrackClick = { showSubtitlePicker = true },
                onAspectModeClick = { showAspectPicker = true },
                onPlaybackSpeedClick = { showSpeedPicker = true },
                onOpenExternally = { viewModel.openExternally(context) },
                onSwitchChannel = viewModel::switchChannel,
            )
        }

        // Floating rather than folded into TvPlayerControls - visible even while the transport bar
        // is hidden (the common case right after an episode starts, before the user has touched
        // anything), matching every other player's "skip intro appears on its own" behavior.
        AnimatedVisibility(
            visible = skipIntroVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomEnd).padding(32.dp),
        ) {
            Button(
                onClick = { introRange?.let { viewModel.seekTo(it.last) } },
                modifier = Modifier.focusRequester(skipIntroFocusRequester),
            ) {
                Text("Skip Intro")
            }
        }

        // Drawn last so it sits above the controls - if both are up, answering the prompt is the
        // more urgent action and shouldn't be behind a transport bar.
        AnimatedVisibility(visible = nextEpisodePromptVisible, enter = fadeIn(), exit = fadeOut()) {
            val prompt = nextItem
            if (prompt != null) {
                NextEpisodePromptScrim {
                    NextEpisodePromptBody(
                        nextItem = prompt,
                        secondsRemaining = nextEpisodeCountdown ?: 0,
                    ) {
                        Button(
                            onClick = viewModel::playNextItem,
                            modifier = Modifier.focusRequester(watchNowFocusRequester),
                        ) {
                            Text("Watch Now")
                        }
                        // Cancel returns to wherever playback was launched from (the episode list
                        // or detail page), rather than just dismissing to a finished video sitting
                        // on its last frame with nothing left to do.
                        Button(onClick = onBack) {
                            Text("Cancel")
                        }
                    }
                }
            }
        }
    }

    if (showAspectPicker) {
        Dialog(onDismissRequest = { showAspectPicker = false }) {
            MaterialTheme {
                Column(modifier = Modifier.background(Palette.SurfaceElevated).padding(24.dp)) {
                    Text(text = "Aspect ratio", color = Color.White)
                    Spacer(modifier = Modifier.padding(top = 12.dp))
                    listOf(
                        "Fit to screen" to VideoAspectMode.FIT,
                        "Fill" to VideoAspectMode.FILL,
                        "4:3" to VideoAspectMode.RATIO_4_3,
                        "16:9" to VideoAspectMode.RATIO_16_9,
                    ).forEach { (label, mode) ->
                        Button(onClick = { viewModel.setAspectMode(mode); showAspectPicker = false }) {
                            Text(if (mode == uiState.aspectMode) "✓ $label" else label)
                        }
                    }
                }
            }
        }
    }

    if (showSpeedPicker) {
        Dialog(onDismissRequest = { showSpeedPicker = false }) {
            MaterialTheme {
                Column(modifier = Modifier.background(Palette.SurfaceElevated).padding(24.dp)) {
                    Text(text = "Playback speed", color = Color.White)
                    Spacer(modifier = Modifier.padding(top = 12.dp))
                    PLAYBACK_SPEED_OPTIONS.forEach { speed ->
                        Button(onClick = { viewModel.setPlaybackSpeed(speed); showSpeedPicker = false }) {
                            Text(if (speed == uiState.playbackSpeed) "✓ ${playbackSpeedLabel(speed)}" else playbackSpeedLabel(speed))
                        }
                    }
                }
            }
        }
    }

    if (showAudioPicker) {
        TvTrackPickerDialog(
            title = "Audio",
            tracks = uiState.audioTracks,
            offSelected = false,
            onSelect = { viewModel.selectAudioTrack(it); showAudioPicker = false },
            onOff = null,
            onDismiss = { showAudioPicker = false },
        )
    }
    if (showSubtitlePicker) {
        TvTrackPickerDialog(
            title = "Subtitles",
            tracks = uiState.subtitleTracks,
            offSelected = uiState.subtitlesOff,
            onSelect = { viewModel.selectSubtitleTrack(it); showSubtitlePicker = false },
            onOff = { viewModel.clearSubtitles(); showSubtitlePicker = false },
            onDismiss = { showSubtitlePicker = false },
        )
    }
}

/** Up's lightweight glance panel - title/episode plus stream stats, no buttons of its own (dismissed the same way it's shown: Down/OK for the full controls, or it just times out). Live content reuses the existing now/next header since that's a strict superset of what this would otherwise show. */
@Composable
private fun TvMediaInfoPanel(uiState: PlayerUiState, currentItem: PlaybackItem?) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(32.dp),
    ) {
        val liveInfo = currentItem?.liveProgramInfo
        if (liveInfo != null) {
            TvLiveProgramHeader(liveInfo = liveInfo, uiState = uiState)
        } else if (currentItem != null) {
            Column {
                Text(text = currentItem.title, color = Color.White)
                currentItem.subtitle?.let { subtitle ->
                    Text(text = subtitle, color = Color.White.copy(alpha = 0.75f), modifier = Modifier.padding(top = 2.dp))
                }
                Row(modifier = Modifier.padding(top = 8.dp)) {
                    statBadgeLabels(uiState).forEach { label ->
                        Box(modifier = Modifier.padding(end = 8.dp).background(Color.White.copy(alpha = 0.15f)).padding(horizontal = 8.dp, vertical = 3.dp)) {
                            Text(text = label, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

/** Shared stats-for-nerds badge list - aspect/resolution/FPS/channels (already shown pre-this-feature) plus codec/bitrate/HDR, used by both TvMediaInfoPanel (VOD/Jellyfin) and TvLiveProgramHeader (Live TV) so the two don't drift into showing different stats for what's otherwise the same panel. */
private fun statBadgeLabels(uiState: PlayerUiState): List<String> = listOf(
    aspectRatioLabel(uiState.videoWidth, uiState.videoHeight),
    resolutionLabel(uiState.videoWidth, uiState.videoHeight),
    frameRateLabel(uiState.videoFrameRate),
    codecLabel(uiState.videoCodecMimeType),
    bitrateLabel(uiState.videoBitrateBps),
    uiState.hdrType.orEmpty(),
    audioChannelsLabel(uiState.audioChannelCount),
    codecLabel(uiState.audioCodecMimeType),
    bitrateLabel(uiState.audioBitrateBps),
).filter { it.isNotBlank() }

@Composable
private fun TvPlayerControls(
    uiState: PlayerUiState,
    currentItem: PlaybackItem?,
    recentChannels: List<PlaybackItem>,
    playPauseFocusRequester: FocusRequester,
    onBack: () -> Unit,
    onPlayPause: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onSeek: (Long) -> Unit,
    onAudioTrackClick: () -> Unit,
    onSubtitleTrackClick: () -> Unit,
    onAspectModeClick: () -> Unit,
    onPlaybackSpeedClick: () -> Unit,
    onOpenExternally: () -> Unit,
    onSwitchChannel: (String) -> Unit,
) {
    val liveInfo = currentItem?.liveProgramInfo

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(32.dp),
    ) {
        Button(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }

        Spacer(modifier = Modifier.weight(1f))

        if (liveInfo != null) {
            TvLiveProgramHeader(liveInfo = liveInfo, uiState = uiState)
            Spacer(modifier = Modifier.padding(top = 8.dp))
        }

        val nowStart = liveInfo?.nowStartAtEpochMs
        val nowEnd = liveInfo?.nowEndAtEpochMs
        if (nowStart != null && nowEnd != null) {
            TvLiveProgressBar(nowStartAtEpochMs = nowStart, nowEndAtEpochMs = nowEnd)
        } else {
            TvSeekBar(uiState = uiState, trickplay = currentItem?.trickplay, onSeek = onSeek)
        }
        uiState.errorMessage?.let { Text(text = it, color = Palette.Error) }

        Spacer(modifier = Modifier.padding(top = 12.dp))

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TvIconButton(Icons.Filled.Replay10, "Back 10 seconds", onClick = onSeekBack)
                TvIconButton(
                    icon = if (uiState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = "Play/Pause",
                    onClick = onPlayPause,
                    modifier = Modifier.focusRequester(playPauseFocusRequester),
                )
                TvIconButton(Icons.Filled.Forward10, "Forward 10 seconds", onClick = onSeekForward)
            }
            Spacer(modifier = Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TvIconButton(Icons.Filled.Audiotrack, "Audio track", onClick = onAudioTrackClick, enabled = uiState.audioTracks.isNotEmpty())
                TvIconButton(Icons.Filled.ClosedCaption, "Subtitles", onClick = onSubtitleTrackClick)
                TvIconButton(Icons.Filled.AspectRatio, "Aspect ratio", onClick = onAspectModeClick)
                // Live content isn't meaningfully speed-adjustable (it's a real-time broadcast) -
                // same liveInfo == null gate the phone player's own speed control uses.
                if (liveInfo == null) {
                    TvIconButton(Icons.Filled.Speed, "Playback speed", onClick = onPlaybackSpeedClick)
                }
                TvIconButton(Icons.Filled.OpenInNew, "Open externally", onClick = onOpenExternally)
            }
        }

        if (currentItem?.isLive == true && recentChannels.isNotEmpty()) {
            Spacer(modifier = Modifier.padding(top = 16.dp))
            Text(text = "Channels", color = Color.White.copy(alpha = 0.7f))
            Spacer(modifier = Modifier.padding(top = 6.dp))
            LazyRow {
                items(recentChannels, key = { it.id }) { channel ->
                    TvRecentChannelTile(
                        channel = channel,
                        isCurrent = channel.id == currentItem.id,
                        onClick = { onSwitchChannel(channel.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TvIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(onClick = onClick, enabled = enabled, modifier = modifier) {
        Icon(icon, contentDescription = contentDescription)
    }
}

@Composable
private fun TvLiveProgramHeader(liveInfo: LiveProgramInfo, uiState: PlayerUiState) {
    Row(verticalAlignment = Alignment.Top) {
        liveInfo.channelLogoUrl?.let { url ->
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(64.dp).background(Color.White.copy(alpha = 0.08f)).padding(8.dp),
            )
            Spacer(modifier = Modifier.padding(start = 12.dp))
        }
        Column {
            Text(text = liveInfo.nowTitle ?: liveInfo.channelName, color = Color.White)
            val headerNowStart = liveInfo.nowStartAtEpochMs
            val headerNowEnd = liveInfo.nowEndAtEpochMs
            val timeRange = if (headerNowStart != null && headerNowEnd != null) {
                ", ${formatClockTime(headerNowStart)} – ${formatClockTime(headerNowEnd)}"
            } else {
                ""
            }
            Text(text = "${liveInfo.channelName}$timeRange", color = Color.White.copy(alpha = 0.75f))
            Row(modifier = Modifier.padding(top = 4.dp)) {
                statBadgeLabels(uiState).forEach { label ->
                    Box(modifier = Modifier.padding(end = 8.dp).background(Color.White.copy(alpha = 0.15f)).padding(horizontal = 8.dp, vertical = 3.dp)) {
                        Text(text = label, color = Color.White)
                    }
                }
            }
            liveInfo.nextTitle?.let { nextTitle ->
                val nextTime = liveInfo.nextStartAtEpochMs?.let { " at ${formatClockTime(it)}" }.orEmpty()
                Text(text = "Next: $nextTitle$nextTime", color = Color.White.copy(alpha = 0.6f), modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

// Real players (MX Player, VLC) never fire an actual seek per key-repeat tick - they move a
// preview position instantly (cheap) and only commit the real, expensive seek once the user
// pauses, plus ramp the step size the longer a direction is held so crossing a 2-hour movie
// doesn't mean holding the key for a minute straight.
private const val SEEK_BAR_COMMIT_DEBOUNCE_MS = 400L
private const val SEEK_BAR_ACCEL_WINDOW_MS = 700L
private val SEEK_BAR_STEP_MS_BY_HOLD = longArrayOf(5_000L, 10_000L, 20_000L, 30_000L)

/**
 * Focusable seek bar for VOD - a real Material3 Slider (recolored, same as the phone player's own
 * scrubber) for the familiar look-and-feel "other players" have, rather than SignalBar's segmented
 * meter styling. The Slider itself stays disabled (display-only) - all actual seeking goes through
 * the surrounding key handler below, so there's exactly one path driving playback position instead
 * of the Slider's own built-in keyboard handling potentially fighting with it. tvFocusBorder makes
 * the D-pad focus state visible the same way every other custom focusable control in this app does.
 */
@Composable
private fun TvSeekBar(uiState: PlayerUiState, trickplay: TrickplayInfo?, onSeek: (Long) -> Unit) {
    // Null = not currently scrubbing (display uiState's own real position). Set the moment
    // Left/Right is first pressed, cleared once SEEK_BAR_COMMIT_DEBOUNCE_MS passes with no further
    // presses - see the LaunchedEffect below, which is what actually calls onSeek.
    var previewPositionMs by remember { mutableStateOf<Long?>(null) }
    var consecutivePresses by remember { mutableIntStateOf(0) }
    var lastPressDirection by remember { mutableIntStateOf(0) }
    var lastPressAtMillis by remember { mutableLongStateOf(0L) }

    val displayPositionMs = previewPositionMs ?: uiState.positionMs

    LaunchedEffect(previewPositionMs) {
        val pending = previewPositionMs ?: return@LaunchedEffect
        delay(SEEK_BAR_COMMIT_DEBOUNCE_MS)
        onSeek(pending)
        previewPositionMs = null
    }

    fun press(direction: Int) {
        val now = System.currentTimeMillis()
        consecutivePresses = if (direction == lastPressDirection && now - lastPressAtMillis <= SEEK_BAR_ACCEL_WINDOW_MS) {
            (consecutivePresses + 1).coerceAtMost(SEEK_BAR_STEP_MS_BY_HOLD.size)
        } else {
            1
        }
        lastPressDirection = direction
        lastPressAtMillis = now
        val stepMs = SEEK_BAR_STEP_MS_BY_HOLD[consecutivePresses - 1]
        previewPositionMs = (displayPositionMs + direction * stepMs).coerceIn(0L, uiState.durationMs.coerceAtLeast(0L))
    }

    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .tvFocusBorder(interactionSource)
            .focusable(interactionSource = interactionSource)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> { press(-1); true }
                    Key.DirectionRight -> { press(1); true }
                    else -> false
                }
            }
            .padding(vertical = 4.dp),
    ) {
        // Only while actively scrubbing (previewPositionMs non-null) - showing it against the real,
        // continuously-advancing position the rest of the time would mean constantly refetching
        // tiles for no reason, and there's nothing useful to preview about "what's playing right now".
        if (previewPositionMs != null && trickplay != null) {
            TrickplayPreview(trickplay = trickplay, positionMs = displayPositionMs, modifier = Modifier.padding(bottom = 8.dp))
        }
        MaterialTheme3(colorScheme = appColorScheme()) {
            Slider(
                value = displayPositionMs.toFloat(),
                onValueChange = {},
                enabled = false,
                valueRange = 0f..uiState.durationMs.coerceAtLeast(1L).toFloat(),
                colors = SliderDefaults.colors(
                    disabledThumbColor = Palette.Accent,
                    disabledActiveTrackColor = Palette.Accent,
                    disabledInactiveTrackColor = Palette.Border,
                ),
            )
        }
        Text(
            text = "${formatPositionMs(displayPositionMs)} / ${formatPositionMs(uiState.durationMs)}",
            color = Color.White,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** Elapsed/total against the EPG-scheduled programme slot (wall-clock time), not the stream's own playback position - live TV isn't seekable via this bar, unlike VOD's +/-10s buttons above. */
@Composable
private fun TvLiveProgressBar(nowStartAtEpochMs: Long, nowEndAtEpochMs: Long) {
    var currentTimeMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(nowStartAtEpochMs, nowEndAtEpochMs) {
        while (true) {
            currentTimeMs = System.currentTimeMillis()
            delay(30_000)
        }
    }
    val (elapsed, total) = liveProgramProgress(nowStartAtEpochMs, nowEndAtEpochMs, currentTimeMs)
    SignalBar(
        progress = elapsed.toFloat() / total.toFloat(),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    )
    Text(text = "${formatPositionMs(elapsed)} / ${formatPositionMs(total)}", color = Color.White)
}

@Composable
private fun TvRecentChannelTile(channel: PlaybackItem, isCurrent: Boolean, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.padding(end = 10.dp).width(96.dp)) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(if (isCurrent) Palette.Accent.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center,
            ) {
                if (channel.posterUrl != null) {
                    AsyncImage(
                        model = channel.posterUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(6.dp),
                    )
                } else {
                    Text(text = channel.title.take(2).uppercase(), color = Color.White)
                }
            }
            Text(text = channel.title, color = Color.White, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun TvTrackPickerDialog(
    title: String,
    tracks: List<TrackOption>,
    offSelected: Boolean,
    onSelect: (String) -> Unit,
    onOff: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        MaterialTheme {
            Column(
                modifier = Modifier
                    .background(Palette.SurfaceElevated)
                    .padding(24.dp),
            ) {
                Text(text = title, color = Color.White)
                Spacer(modifier = Modifier.padding(top = 12.dp))
                LazyColumn {
                    if (onOff != null) {
                        item {
                            Button(onClick = onOff) { Text(if (offSelected) "✓ Off" else "Off") }
                        }
                    }
                    items(tracks) { track ->
                        Button(onClick = { onSelect(track.id) }) {
                            Text(if (track.isSelected && !offSelected) "✓ ${track.label}" else track.label)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Switches the TV's own display mode to whichever supported refresh rate is a clean multiple of
 * the content's frame rate (so 23.976/24fps content gets a 24/48/72Hz mode instead of judder-prone
 * 60Hz pulldown), and restores the system default the moment this composable leaves composition -
 * same "capture nothing, just reset to 0 (system default) on the way out" shape as
 * RestoreBrightnessOnDispose's own reasoning on the phone screen, except display mode has an
 * explicit "system default" sentinel (0) rather than needing to remember a previous value.
 * Re-evaluated per contentFrameRate change (a new item, or ExoPlayer settling on a different
 * decoded frame rate mid-session) rather than once - only meaningfully changes when frame rate
 * does, so LaunchedEffect's own equality check already skips redundant mode switches on its own.
 */
@Composable
private fun MatchRefreshRateWhilePlaying(enabled: Boolean, contentFrameRate: Float) {
    if (!enabled) return
    val activity = LocalContext.current as? Activity ?: return

    LaunchedEffect(contentFrameRate) {
        if (contentFrameRate <= 0f) return@LaunchedEffect
        @Suppress("DEPRECATION") // getSupportedModes() has no display-agnostic replacement before API 30
        val supportedModes = activity.windowManager.defaultDisplay?.supportedModes ?: return@LaunchedEffect
        val bestMode = supportedModes
            .filter { mode -> isCleanRefreshRateMultiple(mode.refreshRate, contentFrameRate) }
            .minByOrNull { mode -> mode.refreshRate }
            ?: return@LaunchedEffect
        activity.window.attributes = activity.window.attributes.apply { preferredDisplayModeId = bestMode.modeId }
    }

    DisposableEffect(Unit) {
        onDispose { activity.window.attributes = activity.window.attributes.apply { preferredDisplayModeId = 0 } }
    }
}

/** Within 2% of a whole-number ratio counts as "clean" - real refresh rates are rarely perfectly exact (23.976 vs 24, 59.94 vs 60), so a strict equality check would reject every legitimate match. */
private fun isCleanRefreshRateMultiple(refreshRate: Float, contentFrameRate: Float): Boolean {
    if (contentFrameRate <= 0f || refreshRate <= 0f) return false
    val ratio = refreshRate / contentFrameRate
    val nearestWhole = ratio.roundToInt()
    if (nearestWhole < 1) return false
    return abs(ratio - nearestWhole) < 0.02f
}
