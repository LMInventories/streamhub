package com.android.streamhub.feature.iptv.livetv

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.exoplayer.ExoPlayer
import com.android.streamhub.core.design.AppShapes
import com.android.streamhub.core.design.Palette
import com.android.streamhub.core.player.PlayerUiState
import com.android.streamhub.core.player.VideoAspectMode
import com.android.streamhub.core.player.VideoSurface
import com.android.streamhub.core.player.aspectRatioLabel
import com.android.streamhub.core.player.audioChannelsLabel
import com.android.streamhub.core.player.frameRateLabel
import com.android.streamhub.core.player.resolutionLabel
import com.android.streamhub.feature.iptv.data.EpgProgram
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
    onPlayPause: () -> Unit,
    onToggleMute: () -> Unit,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LockLandscapeWhileFullscreen()
    HideSystemBarsWhileFullscreen()

    var controlsVisible by remember { mutableStateOf(true) }
    LaunchedEffect(controlsVisible, playerUiState.isPlaying) {
        if (controlsVisible && playerUiState.isPlaying) {
            delay(4000)
            controlsVisible = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) { detectTapGestures { controlsVisible = !controlsVisible } },
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
                        StatBadge(resolutionLabel(playerUiState.videoHeight))
                        StatBadge(frameRateLabel(playerUiState.videoFrameRate))
                        StatBadge(audioChannelsLabel(playerUiState.audioChannelCount))
                    }
                    Row(modifier = Modifier.padding(top = 12.dp)) {
                        OverlayTextButton(if (playerUiState.isPlaying) "Pause" else "Play", onClick = onPlayPause)
                        Spacer(modifier = Modifier.width(12.dp))
                        OverlayTextButton(if (playerUiState.isMuted) "Unmute" else "Mute", onClick = onToggleMute)
                    }
                }
            }
        }
    }
}

@Composable
private fun OverlayTextButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(Color.White.copy(alpha = 0.15f), AppShapes.small)
            .clickable(onClick = onClick)
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
