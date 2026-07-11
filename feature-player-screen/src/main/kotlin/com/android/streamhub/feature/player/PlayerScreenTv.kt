package com.android.streamhub.feature.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
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
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.android.streamhub.core.common.domain.LiveProgramInfo
import com.android.streamhub.core.common.domain.PlaybackItem
import com.android.streamhub.core.design.Palette
import com.android.streamhub.core.design.SignalBar
import com.android.streamhub.core.player.PlayerUiState
import com.android.streamhub.core.player.TrackOption
import com.android.streamhub.core.player.VideoAspectMode
import com.android.streamhub.core.player.VideoSurface
import com.android.streamhub.core.player.audioChannelsLabel
import com.android.streamhub.core.player.aspectRatioLabel
import com.android.streamhub.core.player.formatPositionMs
import com.android.streamhub.core.player.frameRateLabel
import com.android.streamhub.core.player.KeepScreenOnWhilePlaying
import com.android.streamhub.core.player.resolutionLabel
import kotlinx.coroutines.delay

private const val SEEK_STEP_MS = 10_000L

@Composable
fun PlayerScreenTv(
    onBack: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentItem by viewModel.currentItem.collectAsStateWithLifecycle()
    val recentChannels by viewModel.recentChannels.collectAsStateWithLifecycle()

    KeepScreenOnWhilePlaying(isPlaying = uiState.isPlaying)

    var controlsVisible by remember { mutableStateOf(true) }
    var showAudioPicker by remember { mutableStateOf(false) }
    var showSubtitlePicker by remember { mutableStateOf(false) }
    var showAspectPicker by remember { mutableStateOf(false) }
    val backButtonFocusRequester = remember { FocusRequester() }

    LaunchedEffect(controlsVisible, uiState.isPlaying) {
        if (controlsVisible && uiState.isPlaying) {
            delay(6000)
            controlsVisible = false
        }
    }

    // AnimatedVisibility tears the controls' whole focus tree down and rebuilds it every time
    // they auto-hide/reappear (they auto-hide after 6s while playing, above) - Compose's focus
    // system doesn't automatically pick a new focused node when that happens, so without this,
    // D-pad input goes nowhere at all once the controls come back until something else claims
    // focus, which reads as the remote being stuck. runCatching guards a real race: this effect
    // and AnimatedVisibility mounting the Back button it targets are both triggered by the same
    // controlsVisible flip, with no guaranteed ordering between them - if the effect runs first,
    // requestFocus() throws IllegalStateException (target not attached yet) and, uncaught, would
    // crash the whole player rather than just skip a focus request that'll succeed next time.
    LaunchedEffect(controlsVisible) {
        if (controlsVisible) runCatching { backButtonFocusRequester.requestFocus() }
    }

    // Same "OK brings hidden TV controls back" fix as LiveFullscreenOverlay (this screen's own
    // equivalent gap - it's what every VOD/Jellyfin/downloaded item actually plays through). Once
    // controls auto-hide above, every focusable button in them is removed from composition -
    // without a focusable node behind them to catch the key event, a D-pad OK/Enter press had
    // nothing to land on at all and just did nothing, which is exactly what read as "the pause/
    // stop buttons don't work" - they weren't unresponsive, there was nothing left on screen to
    // receive the press in the first place.
    val boxFocusRequester = remember { FocusRequester() }
    LaunchedEffect(controlsVisible) {
        if (!controlsVisible) runCatching { boxFocusRequester.requestFocus() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(boxFocusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (!controlsVisible &&
                    event.type == KeyEventType.KeyDown &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter)
                ) {
                    controlsVisible = true
                    true
                } else {
                    false
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

        AnimatedVisibility(visible = controlsVisible, enter = fadeIn(), exit = fadeOut()) {
            TvPlayerControls(
                uiState = uiState,
                currentItem = currentItem,
                recentChannels = recentChannels,
                backButtonFocusRequester = backButtonFocusRequester,
                onBack = onBack,
                onPlayPause = { controlsVisible = true; viewModel.togglePlayPause() },
                onSeekBack = { viewModel.seekTo((uiState.positionMs - SEEK_STEP_MS).coerceAtLeast(0L)) },
                onSeekForward = { viewModel.seekTo((uiState.positionMs + SEEK_STEP_MS).coerceAtMost(uiState.durationMs)) },
                onAudioTrackClick = { showAudioPicker = true },
                onSubtitleTrackClick = { showSubtitlePicker = true },
                onAspectModeClick = { showAspectPicker = true },
                onOpenExternally = { viewModel.openExternally(context) },
                onSwitchChannel = viewModel::switchChannel,
            )
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

@Composable
private fun TvPlayerControls(
    uiState: PlayerUiState,
    currentItem: PlaybackItem?,
    recentChannels: List<PlaybackItem>,
    backButtonFocusRequester: FocusRequester,
    onBack: () -> Unit,
    onPlayPause: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onAudioTrackClick: () -> Unit,
    onSubtitleTrackClick: () -> Unit,
    onAspectModeClick: () -> Unit,
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
        Button(onClick = onBack, modifier = Modifier.focusRequester(backButtonFocusRequester)) { Text("Back") }

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
            Text(
                text = "${formatPositionMs(uiState.positionMs)} / ${formatPositionMs(uiState.durationMs)}",
                color = Color.White,
            )
        }
        uiState.errorMessage?.let { Text(text = it, color = Palette.Error) }

        Spacer(modifier = Modifier.padding(top = 12.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            Button(onClick = onSeekBack) { Text("-10s") }
            Button(onClick = onPlayPause) { Text(if (uiState.isPlaying) "Pause" else "Play") }
            Button(onClick = onSeekForward) { Text("+10s") }
            Button(onClick = onAudioTrackClick, enabled = uiState.audioTracks.isNotEmpty()) { Text("Audio") }
            Button(onClick = onSubtitleTrackClick) { Text("Subtitles") }
            Button(onClick = onAspectModeClick) { Text("Aspect ratio") }
            Button(onClick = onOpenExternally) { Text("Open externally") }
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
                listOf(
                    aspectRatioLabel(uiState.videoWidth, uiState.videoHeight),
                    resolutionLabel(uiState.videoWidth, uiState.videoHeight),
                    frameRateLabel(uiState.videoFrameRate),
                    audioChannelsLabel(uiState.audioChannelCount),
                ).filter { it.isNotBlank() }.forEach { label ->
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
