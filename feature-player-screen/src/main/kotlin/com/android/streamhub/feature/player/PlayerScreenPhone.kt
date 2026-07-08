package com.android.streamhub.feature.player

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.android.streamhub.core.player.PlayerUiState
import com.android.streamhub.core.player.TrackOption
import com.android.streamhub.core.player.VideoAspectMode
import com.android.streamhub.core.player.VideoSurface
import kotlinx.coroutines.delay

@Composable
fun PlayerScreenPhone(
    onBack: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentItem by viewModel.currentItem.collectAsStateWithLifecycle()
    val recentChannels by viewModel.recentChannels.collectAsStateWithLifecycle()

    var controlsVisible by remember { mutableStateOf(true) }
    var showAudioPicker by remember { mutableStateOf(false) }
    var showSubtitlePicker by remember { mutableStateOf(false) }

    LockLandscapeWhilePlaying()
    HideSystemBarsWhilePlaying()

    LaunchedEffect(controlsVisible, uiState.isPlaying) {
        if (controlsVisible && uiState.isPlaying) {
            delay(4000)
            controlsVisible = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures { controlsVisible = !controlsVisible }
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
            PhonePlayerControls(
                uiState = uiState,
                currentItem = currentItem,
                recentChannels = recentChannels,
                onBack = onBack,
                onPlayPause = viewModel::togglePlayPause,
                onSeek = viewModel::seekTo,
                onAudioTrackClick = { showAudioPicker = true },
                onSubtitleTrackClick = { showSubtitlePicker = true },
                onAspectModeSelected = viewModel::setAspectMode,
                onOpenExternally = { viewModel.openExternally(context) },
                onSwitchChannel = viewModel::switchChannel,
            )
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

@Composable
private fun PhonePlayerControls(
    uiState: PlayerUiState,
    currentItem: PlaybackItem?,
    recentChannels: List<PlaybackItem>,
    onBack: () -> Unit,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onAudioTrackClick: () -> Unit,
    onSubtitleTrackClick: () -> Unit,
    onAspectModeSelected: (VideoAspectMode) -> Unit,
    onOpenExternally: () -> Unit,
    onSwitchChannel: (String) -> Unit,
) {
    var showAspectMenu by remember { mutableStateOf(false) }
    val liveInfo = currentItem?.liveProgramInfo

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Spacer(modifier = Modifier.weight(1f))
            uiState.errorMessage?.let {
                Text(text = it, color = Palette.Error)
            }
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
                Slider(
                    value = uiState.positionMs.toFloat(),
                    onValueChange = { onSeek(it.toLong()) },
                    valueRange = 0f..uiState.durationMs.coerceAtLeast(1L).toFloat(),
                    colors = SliderDefaults.colors(
                        thumbColor = Palette.Accent,
                        activeTrackColor = Palette.Accent,
                        inactiveTrackColor = Palette.Border,
                    ),
                )
                Text(
                    text = "${formatPositionMs(uiState.positionMs)} / ${formatPositionMs(uiState.durationMs)}",
                    color = Color.White,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onPlayPause) {
                    Icon(
                        imageVector = if (uiState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = Color.White,
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
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
                StatBadge(aspectRatioLabel(uiState.videoWidth, uiState.videoHeight))
                StatBadge(resolutionLabel(uiState.videoHeight))
                StatBadge(frameRateLabel(uiState.videoFrameRate))
                StatBadge(audioChannelsLabel(uiState.audioChannelCount))
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
