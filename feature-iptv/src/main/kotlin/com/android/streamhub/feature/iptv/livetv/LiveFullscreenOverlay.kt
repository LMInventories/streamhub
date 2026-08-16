package com.android.streamhub.feature.iptv.livetv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.exoplayer.ExoPlayer
import coil3.compose.AsyncImage
import com.android.streamhub.core.design.AppShapes
import com.android.streamhub.core.design.Palette
import com.android.streamhub.core.design.tvFocusBorder
import com.android.streamhub.core.player.KeepScreenOnWhileOpen
import com.android.streamhub.core.player.PlayerUiState
import com.android.streamhub.core.player.VideoAspectMode
import com.android.streamhub.core.player.VideoSurface
import com.android.streamhub.core.player.aspectRatioLabel
import com.android.streamhub.core.player.audioChannelsLabel
import com.android.streamhub.core.player.frameRateLabel
import com.android.streamhub.core.player.resolutionLabel
import com.android.streamhub.feature.iptv.data.EpgProgram
import com.android.streamhub.feature.iptv.data.IptvCategoryInfo
import com.android.streamhub.feature.iptv.data.IptvChannelInfo
import kotlinx.coroutines.delay

/**
 * Expands the exact same ExoPlayer instance already playing in the mini-preview to fill the
 * screen, in place - no navigation, no second player, no rebuffering. Theme-agnostic
 * (BasicText/Palette, no Material3 Text/Button) since this is shared by both the phone and TV
 * Live TV screens, same reasoning as EpgGridPanel/EpgInfoPanel/CategoryPrefixFilterRow elsewhere
 * in this file's neighborhood.
 */
@Composable
fun LiveFullscreenOverlay(
    exoPlayer: ExoPlayer,
    playerUiState: PlayerUiState,
    channel: IptvChannelInfo,
    nowProgram: EpgProgram?,
    nextProgram: EpgProgram?,
    recentChannels: List<IptvChannelInfo>,
    categories: List<IptvCategoryInfo>,
    multiviewPickerTab: MultiviewPickerTab,
    multiviewPickerChannels: List<IptvChannelInfo>,
    isLoadingMultiviewPickerChannels: Boolean,
    onSelectMultiviewPickerTab: (MultiviewPickerTab) -> Unit,
    onResetMultiviewPicker: () -> Unit,
    onSwitchChannel: (IptvChannelInfo) -> Unit,
    onPlayPause: () -> Unit,
    onToggleMute: () -> Unit,
    onStartMultiview: (IptvChannelInfo) -> Unit,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LockLandscapeWhileFullscreen()
    HideSystemBarsWhileFullscreen()
    // Not KeepScreenOnWhilePlaying(isPlaying = ...) - Player.isPlaying flips false on every
    // transient rebuffer (normal for IPTV live streams), which dropped keepScreenOn mid-stall and
    // let the screensaver/system reclaim the app exactly like the bug KeepScreenOnWhileOpen's own
    // doc describes for the on-demand player. This screen has no real "paused" state to gate on,
    // so it stays on for as long as it's open, same fix already applied there.
    KeepScreenOnWhileOpen()

    // This composable is only ever on screen while fullscreen is active, so Back always means
    // "collapse back to the mini-player/browse view" - without this, Back here had nothing to
    // catch it and fell through to whatever's above (tab-switch history, or the Activity itself,
    // closing the app), same underlying gap as the category/EPG back button below.
    BackHandler(onBack = onCollapse)

    var controlsVisible by remember { mutableStateOf(true) }
    var showMultiviewPicker by remember { mutableStateOf(false) }
    LaunchedEffect(controlsVisible, playerUiState.isPlaying) {
        if (controlsVisible && playerUiState.isPlaying) {
            delay(4000)
            controlsVisible = false
        }
    }

    // Key events only ever reach a node that's focused (or an ancestor of the focused node) -
    // once controls hide, every focusable button in them is removed from composition, so without
    // this the surrounding Box itself never holds focus and the onPreviewKeyEvent below would
    // never actually fire for a D-pad OK press. Re-requested every time controls hide (not just
    // once) since that's exactly when focus needs somewhere new to land.
    val boxFocusRequester = remember { FocusRequester() }
    LaunchedEffect(controlsVisible) {
        if (!controlsVisible) boxFocusRequester.requestFocus()
    }

    // The reverse case: controls just became visible (auto-shown, or brought back via the
    // DirectionCenter handler below) but focus was left sitting on the outer Box from the branch
    // above - with nothing here to move it, the Box's own .focusable() kept eating every D-pad
    // press (no arrow-key handling, no click-on-Enter) while every button underneath sat
    // unreachable, which is what made the whole control row look completely dead on a TV remote.
    // Landing on Play/Pause specifically (same choice PlayerScreenTv.kt's own overlay makes) puts
    // the single most likely next press somewhere useful immediately.
    val playPauseFocusRequester = remember { FocusRequester() }
    LaunchedEffect(controlsVisible) {
        if (controlsVisible) runCatching { playPauseFocusRequester.requestFocus() }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(boxFocusRequester)
            .focusable()
            .pointerInput(Unit) { detectTapGestures { controlsVisible = !controlsVisible } }
            // detectTapGestures above only ever fires for a touch tap - a TV remote's D-pad
            // center/OK press is a key event, not a pointer event, so on TV there was previously
            // no way at all to bring the controls back once they auto-hid. Only handled while
            // hidden - once controls are showing, OK should activate whatever button is actually
            // focused, not toggle visibility again.
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
        VideoSurface(exoPlayer = exoPlayer, aspectMode = VideoAspectMode.FIT, modifier = Modifier.fillMaxSize())

        if (playerUiState.isBuffering) {
            CircularProgressIndicator(color = Palette.Accent, modifier = Modifier.align(Alignment.Center))
        }

        if (controlsVisible) {
            Column(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f))) {
                Row(modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp)) {
                    OverlayTextButton("‹ Back", onClick = onCollapse)
                }

                Spacer(modifier = Modifier.weight(1f))

                Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)) {
                    EpgInfoPanel(channelName = channel.name, nowProgram = nowProgram, nextProgram = nextProgram)
                    Row(modifier = Modifier.padding(top = 8.dp)) {
                        StatBadge(aspectRatioLabel(playerUiState.videoWidth, playerUiState.videoHeight))
                        StatBadge(resolutionLabel(playerUiState.videoWidth, playerUiState.videoHeight))
                        StatBadge(frameRateLabel(playerUiState.videoFrameRate))
                        StatBadge(audioChannelsLabel(playerUiState.audioChannelCount))
                    }
                    Row(modifier = Modifier.padding(top = 12.dp)) {
                        OverlayTextButton(
                            if (playerUiState.isPlaying) "Pause" else "Play",
                            onClick = onPlayPause,
                            modifier = Modifier.focusRequester(playPauseFocusRequester),
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        OverlayTextButton(if (playerUiState.isMuted) "Unmute" else "Mute", onClick = onToggleMute)
                        Spacer(modifier = Modifier.width(12.dp))
                        OverlayTextButton(
                            "Multiview",
                            onClick = {
                                onResetMultiviewPicker()
                                showMultiviewPicker = true
                            },
                        )
                    }

                    if (recentChannels.isNotEmpty()) {
                        BasicText(
                            text = "Channels",
                            style = TextStyle(color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp),
                            modifier = Modifier.padding(top = 16.dp, bottom = 6.dp),
                        )
                        LazyRow {
                            items(recentChannels, key = { it.id }) { recentChannel ->
                                RecentChannelTile(
                                    channel = recentChannel,
                                    isCurrent = recentChannel.id == channel.id,
                                    onClick = { onSwitchChannel(recentChannel) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showMultiviewPicker) {
        MultiviewChannelPicker(
            categories = categories,
            activeTab = multiviewPickerTab,
            recentChannels = recentChannels,
            tabChannels = multiviewPickerChannels,
            isLoadingTabChannels = isLoadingMultiviewPickerChannels,
            excludeChannelIds = setOf(channel.id),
            onSelectTab = onSelectMultiviewPickerTab,
            onPick = { picked ->
                onStartMultiview(picked)
                showMultiviewPicker = false
            },
            onDismiss = { showMultiviewPicker = false },
        )
    }
}

@Composable
private fun RecentChannelTile(channel: IptvChannelInfo, isCurrent: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = Modifier
            .padding(end = 10.dp)
            .width(72.dp)
            .tvFocusBorder(interactionSource, AppShapes.small)
            .clickable(interactionSource = interactionSource, indication = LocalIndication.current, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(AppShapes.small)
                .background(if (isCurrent) Palette.Accent.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center,
        ) {
            if (channel.logoUrl != null) {
                AsyncImage(
                    model = channel.logoUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(6.dp),
                )
            } else {
                BasicText(text = channel.name.take(2).uppercase(), style = TextStyle(color = Color.White, fontSize = 14.sp))
            }
        }
        BasicText(
            text = channel.name,
            style = TextStyle(color = Color.White, fontSize = 10.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}

@Composable
private fun OverlayTextButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .background(Color.White.copy(alpha = 0.15f), AppShapes.small)
            .tvFocusBorder(interactionSource, AppShapes.small)
            .clickable(interactionSource = interactionSource, indication = LocalIndication.current, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        BasicText(text = label, style = TextStyle(color = Color.White, fontSize = 13.sp))
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
        BasicText(text = text, style = TextStyle(color = Color.White, fontSize = 11.sp))
    }
}
