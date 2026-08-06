package com.android.streamhub.feature.player

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.android.streamhub.core.common.domain.LiveProgramInfo
import com.android.streamhub.core.common.domain.PlaybackItem
import com.android.streamhub.core.design.AppShapes
import com.android.streamhub.core.design.Palette
import com.android.streamhub.core.design.SignalBar
import com.android.streamhub.core.player.KeepScreenOnWhilePlaying
import com.android.streamhub.core.player.PLAYBACK_SPEED_OPTIONS
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
import com.android.streamhub.core.player.playbackSpeedLabel
import com.android.streamhub.core.player.resolutionLabel
import com.android.streamhub.feature.player.cast.CastButton
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

// Hardcoded for now, not read from anywhere - this becomes a real setting once general player
// settings exist, but there's nowhere for that to live yet.
private const val DOUBLE_TAP_SEEK_MS = 10_000L

// A full-width horizontal drag seeks 90 seconds - fast enough to cross a long movie without an
// unreasonably long swipe, gentle enough that a short correction near the end of a drag doesn't
// blow past the intended target.
private const val SEEK_GESTURE_SPAN_MS = 90_000L

// Below this many pixels of total movement, a drag-in-progress isn't classified into
// seek/brightness/volume yet - keeps a genuine tap (which necessarily wobbles a pixel or two
// before lifting) from ever being misread as the start of a gesture.
private const val GESTURE_SLOP_PX = 12f

private enum class PhoneGestureMode { SEEK, BRIGHTNESS, VOLUME }

@Composable
fun PlayerScreenPhone(
    onBack: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentItem by viewModel.currentItem.collectAsStateWithLifecycle()
    val recentChannels by viewModel.recentChannels.collectAsStateWithLifecycle()
    val nextItem by viewModel.nextItem.collectAsStateWithLifecycle()
    val creditsStartMs by viewModel.creditsStartMs.collectAsStateWithLifecycle()
    val introRange by viewModel.introRange.collectAsStateWithLifecycle()

    // Non-null once the episode's real credits start (if known) or, failing that, in the last ten
    // seconds of an episode that actually has a follow-up - see nextEpisodeCountdownSeconds. Live
    // content never qualifies (no meaningful duration).
    val nextEpisodeCountdown = nextItem
        ?.takeIf { currentItem?.isLive == false }
        ?.let { nextEpisodeCountdownSeconds(uiState.positionMs, uiState.durationMs, creditsStartMs) }

    LaunchedEffect(nextEpisodeCountdown) {
        if (nextEpisodeCountdown == 0) viewModel.playNextItem()
    }

    // Visible only while actually inside the analyzed intro window - see MediaSource.resolvePlaybackSegments.
    val skipIntroVisible = introRange?.let { uiState.positionMs in it } == true && currentItem?.isLive == false

    var controlsVisible by remember { mutableStateOf(true) }
    var showAudioPicker by remember { mutableStateOf(false) }
    var showSubtitlePicker by remember { mutableStateOf(false) }
    // null = no feedback showing; true/false = seeking forward/backward, just tapped.
    var seekFeedback by remember { mutableStateOf<Boolean?>(null) }

    LockLandscapeWhilePlaying()
    HideSystemBarsWhilePlaying()
    RestoreBrightnessOnDispose()
    KeepScreenOnWhilePlaying(isPlaying = uiState.isPlaying)

    // Back closes the control overlay rather than leaving playback whenever the overlay is up -
    // mirrors PlayerScreenTv's remote Back behavior; only falls through to onBack (leaving
    // playback) once the controls are already hidden.
    BackHandler(enabled = controlsVisible) {
        controlsVisible = false
    }

    // Bumped by every tap/click inside the controls and included below as a LaunchedEffect key -
    // reassigning controlsVisible to the value it already holds (e.g. tapping Play/Pause again
    // while controls are showing) doesn't recompose on its own since Compose state skips no-op
    // writes, so without this the hide countdown would keep ticking from when the controls first
    // appeared instead of resetting on continued use.
    var interactionTick by remember { mutableIntStateOf(0) }

    LaunchedEffect(controlsVisible, uiState.isPlaying, interactionTick) {
        if (controlsVisible && uiState.isPlaying) {
            delay(3000)
            controlsVisible = false
        }
    }

    // detectTapGestures(Unit) only starts its gesture-detection coroutine once (the key never
    // changes) - reading uiState/currentItem directly inside its callbacks would close over
    // whatever those were on the very first composition. rememberUpdatedState keeps a stable
    // holder whose .value is always current, so onDoubleTap below always seeks from the real
    // playback position and respects the real isLive state, not a stale first-frame snapshot.
    val latestUiState by rememberUpdatedState(uiState)
    val latestCurrentItem by rememberUpdatedState(currentItem)

    LaunchedEffect(seekFeedback) {
        if (seekFeedback != null) {
            delay(500)
            seekFeedback = null
        }
    }

    // Gesture-drag state - null means "not currently showing that feedback". Continuously
    // reassigned while a finger is down (see the drag handler below), so each LaunchedEffect below
    // keeps restarting its hide-delay for as long as the value keeps changing, then actually hides
    // it 700ms after the last change - same "reset countdown on continued activity" shape
    // controlsVisible's own LaunchedEffect above already uses.
    var seekPreviewMs by remember { mutableStateOf<Long?>(null) }
    var brightnessLevel by remember { mutableStateOf<Float?>(null) }
    var volumeLevel by remember { mutableStateOf<Float?>(null) }

    LaunchedEffect(brightnessLevel) {
        if (brightnessLevel != null) { delay(700); brightnessLevel = null }
    }
    LaunchedEffect(volumeLevel) {
        if (volumeLevel != null) { delay(700); volumeLevel = null }
    }

    val activity = context as? Activity
    val audioManager = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                // One continuous drag can only ever mean one of these three things - which one is
                // decided once real movement clears GESTURE_SLOP_PX (so a drag that turns out to be
                // a near-stationary tap never gets misclassified), then locked in for the rest of
                // that same gesture so a slightly diagonal finger movement can't flip between
                // seeking and adjusting volume/brightness mid-drag.
                var mode: PhoneGestureMode? = null
                var startX = 0f
                var accumX = 0f
                var accumY = 0f
                var startBrightness = 0.5f
                var startVolumeFraction = 0f
                detectDragGestures(
                    onDragStart = { offset ->
                        mode = null
                        startX = offset.x
                        accumX = 0f
                        accumY = 0f
                        startBrightness = activity?.window?.attributes?.screenBrightness
                            ?.takeIf { it in 0f..1f } ?: 0.5f
                        startVolumeFraction = audioManager?.let { manager ->
                            val max = manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                            manager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max
                        } ?: 0f
                    },
                    onDragEnd = {
                        seekPreviewMs?.let { viewModel.seekTo(it) }
                        seekPreviewMs = null
                        mode = null
                    },
                    onDragCancel = {
                        seekPreviewMs = null
                        mode = null
                    },
                ) { change, dragAmount ->
                    change.consume()
                    accumX += dragAmount.x
                    accumY += dragAmount.y
                    if (mode == null) {
                        if (abs(accumX) < GESTURE_SLOP_PX && abs(accumY) < GESTURE_SLOP_PX) return@detectDragGestures
                        mode = when {
                            abs(accumX) > abs(accumY) -> PhoneGestureMode.SEEK
                            startX < size.width / 2f -> PhoneGestureMode.BRIGHTNESS
                            else -> PhoneGestureMode.VOLUME
                        }
                    }
                    when (mode) {
                        PhoneGestureMode.SEEK -> {
                            val item = latestCurrentItem
                            if (item != null && !item.isLive) {
                                val durationMs = latestUiState.durationMs.coerceAtLeast(0L)
                                // accumX is the total drag distance since this gesture started, not
                                // a per-frame delta - so this is always relative to the position
                                // playback was actually at when the finger went down, not to
                                // whatever seekPreviewMs last displayed.
                                val deltaMs = (accumX / size.width.coerceAtLeast(1) * SEEK_GESTURE_SPAN_MS).toLong()
                                seekPreviewMs = (latestUiState.positionMs + deltaMs).coerceIn(0L, durationMs)
                            }
                        }
                        PhoneGestureMode.BRIGHTNESS -> {
                            val level = (startBrightness - accumY / size.height.coerceAtLeast(1)).coerceIn(0.01f, 1f)
                            activity?.window?.let { window -> window.attributes = window.attributes.apply { screenBrightness = level } }
                            brightnessLevel = level
                        }
                        PhoneGestureMode.VOLUME -> {
                            val level = (startVolumeFraction - accumY / size.height.coerceAtLeast(1)).coerceIn(0f, 1f)
                            audioManager?.let { manager ->
                                val max = manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                manager.setStreamVolume(AudioManager.STREAM_MUSIC, (level * max).roundToInt(), 0)
                            }
                            volumeLevel = level
                        }
                        null -> {}
                    }
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { controlsVisible = !controlsVisible; interactionTick++ },
                    onDoubleTap = { offset ->
                        // Live TV isn't meaningfully seekable (its slider above is a read-only
                        // wall-clock progress bar, not a scrubber) - VOD/Jellyfin playback is
                        // exactly everything that isn't live, so that's the gate rather than
                        // hardcoding a source-type allowlist that'd need updating for every new
                        // non-live source.
                        val item = latestCurrentItem ?: return@detectTapGestures
                        if (item.isLive) return@detectTapGestures
                        val forward = offset.x > size.width / 2
                        val target = if (forward) {
                            (latestUiState.positionMs + DOUBLE_TAP_SEEK_MS).coerceAtMost(latestUiState.durationMs)
                        } else {
                            (latestUiState.positionMs - DOUBLE_TAP_SEEK_MS).coerceAtLeast(0L)
                        }
                        viewModel.seekTo(target)
                        seekFeedback = forward
                    },
                )
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

        seekFeedback?.let { forward ->
            SeekFeedbackIndicator(
                forward = forward,
                seekSeconds = (DOUBLE_TAP_SEEK_MS / 1000).toInt(),
                modifier = Modifier.align(if (forward) Alignment.CenterEnd else Alignment.CenterStart).padding(32.dp),
            )
        }

        seekPreviewMs?.let { previewMs ->
            GesturePill(
                icon = if (previewMs >= uiState.positionMs) Icons.Filled.FastForward else Icons.Filled.FastRewind,
                text = "${formatPositionMs(previewMs)} / ${formatPositionMs(uiState.durationMs)}",
                modifier = Modifier.align(Alignment.Center),
            )
        }
        brightnessLevel?.let { level ->
            GesturePill(
                icon = Icons.Filled.BrightnessMedium,
                text = "${(level * 100).roundToInt()}%",
                modifier = Modifier.align(Alignment.CenterStart).padding(32.dp),
            )
        }
        volumeLevel?.let { level ->
            GesturePill(
                icon = if (level <= 0f) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                text = "${(level * 100).roundToInt()}%",
                modifier = Modifier.align(Alignment.CenterEnd).padding(32.dp),
            )
        }

        AnimatedVisibility(visible = controlsVisible, enter = fadeIn(), exit = fadeOut()) {
            PhonePlayerControls(
                uiState = uiState,
                currentItem = currentItem,
                recentChannels = recentChannels,
                isCastAvailable = viewModel.isCastAvailable,
                onBack = onBack,
                onPlayPause = { interactionTick++; viewModel.togglePlayPause() },
                onSeek = { interactionTick++; viewModel.seekTo(it) },
                onSeekBy = { delta -> interactionTick++; viewModel.seekTo((uiState.positionMs + delta).coerceIn(0L, uiState.durationMs.coerceAtLeast(0L))) },
                onAudioTrackClick = { interactionTick++; showAudioPicker = true },
                onSubtitleTrackClick = { interactionTick++; showSubtitlePicker = true },
                onAspectModeSelected = { interactionTick++; viewModel.setAspectMode(it) },
                onPlaybackSpeedSelected = { interactionTick++; viewModel.setPlaybackSpeed(it) },
                onOpenExternally = { interactionTick++; viewModel.openExternally(context) },
                onSwitchChannel = { interactionTick++; viewModel.switchChannel(it) },
            )
        }

        // Floating rather than folded into PhonePlayerControls - visible even while the transport
        // bar is hidden (the common case right after an episode starts, before the user has
        // touched anything), matching every other player's "skip intro appears on its own" behavior.
        AnimatedVisibility(
            visible = skipIntroVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(16.dp),
        ) {
            Button(onClick = { introRange?.let { viewModel.seekTo(it.last) } }) {
                Text("Skip Intro")
            }
        }

        // Drawn last so it sits above the controls - if both are up, answering the prompt is the
        // more urgent action and shouldn't be behind a transport bar. The scrim consumes taps so
        // the root detectTapGestures above can't toggle the controls out from under it.
        AnimatedVisibility(visible = nextEpisodeCountdown != null, enter = fadeIn(), exit = fadeOut()) {
            val prompt = nextItem
            if (prompt != null) {
                Box(modifier = Modifier.pointerInput(Unit) { detectTapGestures { } }) {
                    NextEpisodePromptScrim {
                        NextEpisodePromptBody(
                            nextItem = prompt,
                            secondsRemaining = nextEpisodeCountdown ?: 0,
                        ) {
                            Button(onClick = viewModel::playNextItem) {
                                Text("Watch Now")
                            }
                            // Cancel returns to wherever playback was launched from (the episode
                            // list or detail page), rather than just dismissing to a finished video
                            // sitting on its last frame with nothing left to do.
                            Button(onClick = onBack) {
                                Text("Cancel")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAudioPicker) {
        TrackPickerBottomSheet(
            title = "Audio",
            tracks = uiState.audioTracks,
            offSelected = false,
            onSelect = { viewModel.selectAudioTrack(it); showAudioPicker = false },
            onOff = null,
            onDismiss = { showAudioPicker = false },
        )
    }
    if (showSubtitlePicker) {
        TrackPickerBottomSheet(
            title = "Subtitles",
            tracks = uiState.subtitleTracks,
            offSelected = uiState.subtitlesOff,
            onSelect = { viewModel.selectSubtitleTrack(it); showSubtitlePicker = false },
            onOff = { viewModel.clearSubtitles(); showSubtitlePicker = false },
            onDismiss = { showSubtitlePicker = false },
        )
    }
}

/** Standard video-player UX: force landscape while this screen is on-screen, restore afterward. */
@Composable
private fun LockLandscapeWhilePlaying() {
    val activity = LocalContext.current as? Activity ?: return
    DisposableEffect(Unit) {
        val previousOrientation = activity.requestedOrientation
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        onDispose { activity.requestedOrientation = previousOrientation }
    }
}

/** True fullscreen: hides the status bar and nav bar while playing, restores both on exit. Swiping from an edge still temporarily reveals them (BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE) rather than requiring a settings dive to get them back. */
@Composable
private fun HideSystemBarsWhilePlaying() {
    val activity = LocalContext.current as? Activity ?: return
    DisposableEffect(Unit) {
        val window = activity.window
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        val previousSystemBarsBehavior = controller.systemBarsBehavior

        WindowCompat.setDecorFitsSystemWindows(window, false)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())

        onDispose {
            WindowCompat.setDecorFitsSystemWindows(window, true)
            controller.show(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = previousSystemBarsBehavior
        }
    }
}

/** The brightness-gesture handler in PlayerScreenPhone writes straight to the window's own attributes (there's no other way to change screen brightness short of the system Settings brightness, which isn't per-app) - captured/restored the same way orientation and system-bar visibility already are above, so leaving the player doesn't leave the rest of the OS at whatever brightness the user last dragged to mid-video. */
@Composable
private fun RestoreBrightnessOnDispose() {
    val activity = LocalContext.current as? Activity ?: return
    DisposableEffect(Unit) {
        val window = activity.window
        val previousBrightness = window.attributes.screenBrightness
        onDispose { window.attributes = window.attributes.apply { screenBrightness = previousBrightness } }
    }
}

@Composable
private fun PhonePlayerControls(
    uiState: PlayerUiState,
    currentItem: PlaybackItem?,
    recentChannels: List<PlaybackItem>,
    isCastAvailable: Boolean,
    onBack: () -> Unit,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onSeekBy: (Long) -> Unit,
    onAudioTrackClick: () -> Unit,
    onSubtitleTrackClick: () -> Unit,
    onAspectModeSelected: (VideoAspectMode) -> Unit,
    onPlaybackSpeedSelected: (Float) -> Unit,
    onOpenExternally: () -> Unit,
    onSwitchChannel: (String) -> Unit,
) {
    var showAspectMenu by remember { mutableStateOf(false) }
    var showSpeedMenu by remember { mutableStateOf(false) }
    var showStats by remember { mutableStateOf(false) }
    val liveInfo = currentItem?.liveProgramInfo

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f)),
    ) {
        // Title/cast row - matches the official Jellyfin app's player top bar. No group/person
        // icon (explicitly out of scope per feedback); cast is phone-only, same reasoning
        // CastButton's own call site in Live TV already established (casting makes sense from a
        // handheld to a TV, not from a TV device to itself).
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text(
                text = listOfNotNull(currentItem?.subtitle, currentItem?.title).joinToString(" - "),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
            )
            CastButton(isAvailable = isCastAvailable)
        }
        uiState.errorMessage?.let {
            Text(text = it, color = Palette.Error, modifier = Modifier.padding(horizontal = 16.dp))
        }

        Spacer(modifier = Modifier.weight(1f))

        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)) {
            if (liveInfo != null) {
                LiveProgramHeader(liveInfo = liveInfo, uiState = uiState)
                Spacer(modifier = Modifier.height(10.dp))
            }

            val nowStart = liveInfo?.nowStartAtEpochMs
            val nowEnd = liveInfo?.nowEndAtEpochMs
            if (nowStart != null && nowEnd != null) {
                LiveProgressBar(nowStartAtEpochMs = nowStart, nowEndAtEpochMs = nowEnd)
            } else {
                val sliderInteractionSource = remember { MutableInteractionSource() }
                val isDraggingSlider by sliderInteractionSource.collectIsDraggedAsState()
                val trickplay = currentItem?.trickplay
                // Only while the finger is actually down - see TvSeekBar's matching comment for why
                // this doesn't also show against the real, continuously-advancing position.
                if (isDraggingSlider && trickplay != null) {
                    TrickplayPreview(
                        trickplay = trickplay,
                        positionMs = uiState.positionMs,
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp),
                    )
                }
                Slider(
                    value = uiState.positionMs.toFloat(),
                    onValueChange = { onSeek(it.toLong()) },
                    valueRange = 0f..uiState.durationMs.coerceAtLeast(1L).toFloat(),
                    interactionSource = sliderInteractionSource,
                    colors = SliderDefaults.colors(
                        thumbColor = Palette.Accent,
                        activeTrackColor = Palette.Accent,
                        inactiveTrackColor = Palette.Border,
                    ),
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = formatPositionMs(uiState.positionMs), color = Color.White)
                    Text(text = "-${formatPositionMs((uiState.durationMs - uiState.positionMs).coerceAtLeast(0L))}", color = Color.White)
                }
                // VOD has no always-on header to hang stats off of the way LiveProgramHeader does
                // for live - shown here instead, gated behind the toggle in the controls row below.
                if (showStats) {
                    Row(modifier = Modifier.padding(top = 6.dp)) {
                        statBadgeLabels(uiState).forEach { StatBadge(it) }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (liveInfo == null) {
                    IconButton(onClick = { onSeekBy(-DOUBLE_TAP_SEEK_MS) }) {
                        Icon(Icons.Filled.Replay10, contentDescription = "Back 10 seconds", tint = Color.White)
                    }
                }
                IconButton(onClick = onPlayPause) {
                    Icon(
                        imageVector = if (uiState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = Color.White,
                    )
                }
                if (liveInfo == null) {
                    IconButton(onClick = { onSeekBy(DOUBLE_TAP_SEEK_MS) }) {
                        Icon(Icons.Filled.Forward10, contentDescription = "Forward 10 seconds", tint = Color.White)
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                if (liveInfo == null) {
                    IconButton(onClick = { showStats = !showStats }) {
                        Icon(Icons.Filled.Info, contentDescription = "Stream info", tint = if (showStats) Palette.Accent else Color.White)
                    }
                }
                IconButton(onClick = onAudioTrackClick, enabled = uiState.audioTracks.isNotEmpty()) {
                    Icon(Icons.Filled.Audiotrack, contentDescription = "Audio track", tint = Color.White)
                }
                IconButton(onClick = onSubtitleTrackClick) {
                    Icon(Icons.Filled.ClosedCaption, contentDescription = "Subtitles", tint = Color.White)
                }
                Box {
                    IconButton(onClick = { showAspectMenu = true }) {
                        Icon(Icons.Filled.AspectRatio, contentDescription = "Aspect ratio", tint = Color.White)
                    }
                    DropdownMenu(expanded = showAspectMenu, onDismissRequest = { showAspectMenu = false }) {
                        AspectMenuItem("Fit to screen", VideoAspectMode.FIT, uiState.aspectMode, onAspectModeSelected) { showAspectMenu = false }
                        AspectMenuItem("Fill", VideoAspectMode.FILL, uiState.aspectMode, onAspectModeSelected) { showAspectMenu = false }
                        AspectMenuItem("4:3", VideoAspectMode.RATIO_4_3, uiState.aspectMode, onAspectModeSelected) { showAspectMenu = false }
                        AspectMenuItem("16:9", VideoAspectMode.RATIO_16_9, uiState.aspectMode, onAspectModeSelected) { showAspectMenu = false }
                    }
                }
                // Live content isn't meaningfully speed-adjustable (it's a real-time broadcast) -
                // same liveInfo == null gate the seek buttons already use.
                if (liveInfo == null) {
                    Box {
                        IconButton(onClick = { showSpeedMenu = true }) {
                            Icon(Icons.Filled.Speed, contentDescription = "Playback speed", tint = Color.White)
                        }
                        DropdownMenu(expanded = showSpeedMenu, onDismissRequest = { showSpeedMenu = false }) {
                            PLAYBACK_SPEED_OPTIONS.forEach { speed ->
                                DropdownMenuItem(
                                    text = { Text(if (speed == uiState.playbackSpeed) "${playbackSpeedLabel(speed)} ✓" else playbackSpeedLabel(speed)) },
                                    onClick = { onPlaybackSpeedSelected(speed); showSpeedMenu = false },
                                )
                            }
                        }
                    }
                }
                IconButton(onClick = onOpenExternally) {
                    Icon(Icons.Filled.OpenInNew, contentDescription = "Open externally", tint = Color.White)
                }
            }

            if (currentItem?.isLive == true && recentChannels.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Channels",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                LazyRow {
                    items(recentChannels, key = { it.id }) { channel ->
                        RecentChannelTile(
                            channel = channel,
                            isCurrent = channel.id == currentItem.id,
                            onClick = { onSwitchChannel(channel.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveProgramHeader(liveInfo: LiveProgramInfo, uiState: PlayerUiState) {
    Row(verticalAlignment = Alignment.Top) {
        liveInfo.channelLogoUrl?.let { url ->
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(56.dp).clip(AppShapes.small).background(Color.White.copy(alpha = 0.08f)).padding(6.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
        }
        Column {
            Text(text = liveInfo.nowTitle ?: liveInfo.channelName, color = Color.White, fontSize = 20.sp)
            val nowStart = liveInfo.nowStartAtEpochMs
            val nowEnd = liveInfo.nowEndAtEpochMs
            val timeRange = if (nowStart != null && nowEnd != null) {
                ", ${formatClockTime(nowStart)} – ${formatClockTime(nowEnd)}"
            } else {
                ""
            }
            Text(
                text = "${liveInfo.channelName}$timeRange",
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
            Row(modifier = Modifier.padding(top = 4.dp)) {
                statBadgeLabels(uiState).forEach { StatBadge(it) }
            }
            liveInfo.nextTitle?.let { nextTitle ->
                val nextTime = liveInfo.nextStartAtEpochMs?.let { " at ${formatClockTime(it)}" }.orEmpty()
                Text(
                    text = "Next: $nextTitle$nextTime",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

/** Shared stats-for-nerds badge list - aspect/resolution/FPS/channels (already shown pre-this-feature) plus codec/bitrate/HDR. Used both by LiveProgramHeader (Live TV, always visible) and the VOD stats toggle below (since VOD playback has no always-on header to hang these off of). */
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
private fun StatBadge(text: String) {
    if (text.isBlank()) return
    Box(
        modifier = Modifier
            .padding(end = 6.dp)
            .background(Color.White.copy(alpha = 0.15f), AppShapes.extraSmall)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(text = text, color = Color.White, fontSize = 11.sp)
    }
}

/** Brief confirmation that a double-tap seek registered, since the tap itself has no other visible effect until the slider catches up. */
@Composable
private fun SeekFeedbackIndicator(forward: Boolean, seekSeconds: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(AppShapes.large)
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (forward) Icons.Filled.FastForward else Icons.Filled.FastRewind,
            contentDescription = null,
            tint = Color.White,
        )
        Text(text = "${seekSeconds}s", color = Color.White, modifier = Modifier.padding(start = 4.dp))
    }
}

/** Same pill styling as SeekFeedbackIndicator above - shared by the seek/brightness/volume drag-gesture feedback, which all just want an icon plus a short piece of text. */
@Composable
private fun GesturePill(icon: ImageVector, text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(AppShapes.large)
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = Color.White)
        Text(text = text, color = Color.White, modifier = Modifier.padding(start = 6.dp))
    }
}

/** Elapsed/total against the EPG-scheduled programme slot (wall-clock time), not the stream's own playback position - a live broadcast isn't seekable, so this is read-only, unlike the VOD slider above it. */
@Composable
private fun LiveProgressBar(nowStartAtEpochMs: Long, nowEndAtEpochMs: Long) {
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
private fun RecentChannelTile(channel: PlaybackItem, isCurrent: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier.padding(end = 10.dp).width(72.dp).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(AppShapes.small)
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
                Text(text = channel.title.take(2).uppercase(), color = Color.White, fontSize = 14.sp)
            }
        }
        Text(
            text = channel.title,
            color = Color.White,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}

@Composable
private fun AspectMenuItem(
    label: String,
    mode: VideoAspectMode,
    current: VideoAspectMode,
    onSelected: (VideoAspectMode) -> Unit,
    onDismiss: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(if (mode == current) "$label ✓" else label) },
        onClick = { onSelected(mode); onDismiss() },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrackPickerBottomSheet(
    title: String,
    tracks: List<TrackOption>,
    offSelected: Boolean,
    onSelect: (String) -> Unit,
    onOff: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn {
            item {
                Text(text = title, modifier = Modifier.padding(16.dp))
            }
            if (onOff != null) {
                item {
                    ListItem(
                        headlineContent = { Text("Off") },
                        trailingContent = {
                            if (offSelected) Icon(Icons.Filled.Check, contentDescription = null)
                        },
                        modifier = Modifier.clickable(onClick = onOff),
                    )
                }
            }
            items(tracks) { track ->
                ListItem(
                    headlineContent = { Text(track.label) },
                    trailingContent = {
                        if (track.isSelected && !offSelected) {
                            Icon(Icons.Filled.Check, contentDescription = null)
                        }
                    },
                    modifier = Modifier.clickable { onSelect(track.id) },
                )
            }
        }
    }
}
