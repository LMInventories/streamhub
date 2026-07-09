package com.android.streamhub.feature.iptv.livetv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.streamhub.core.design.AppShapes
import com.android.streamhub.core.design.Palette
import com.android.streamhub.core.player.VideoAspectMode
import com.android.streamhub.core.player.VideoSurface
import com.android.streamhub.feature.iptv.data.IptvChannelInfo

/**
 * Equal-size grid only (1x2 for two tiles, 2x2 for three-with-one-empty-slot or four) - no
 * asymmetric "one big + several small" layout yet, which real multiview apps (TiviMate) also
 * offer as an option but which needs its own picker UI. Only one tile has audio at a time -
 * tapping a tile gives it focus, same "one active stream, everything else muted" convention
 * every established multiview IPTV app uses, both because simultaneous audio from several
 * streams is unusable and because it mirrors this app's own single-focus mini-player already.
 */
@Composable
fun MultiviewOverlay(
    tiles: List<MultiviewTile>,
    audioFocusChannelId: String?,
    pickerCandidates: List<IptvChannelInfo>,
    onTapTile: (channelId: String) -> Unit,
    onRemoveTile: (channelId: String) -> Unit,
    onAddChannel: (IptvChannelInfo) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showAddChannelPicker by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize().background(Color.Black)) {
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close multiview", tint = Color.White)
            }
            BasicText(
                text = "Multiview",
                style = TextStyle(color = Color.White, fontSize = 16.sp),
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        when (tiles.size) {
            0 -> Unit
            1, 2 -> Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                tiles.forEach { tile ->
                    MultiviewTileView(
                        tile = tile,
                        isFocused = tile.channel.id == audioFocusChannelId,
                        onTap = { onTapTile(tile.channel.id) },
                        onRemove = { onRemoveTile(tile.channel.id) },
                        modifier = Modifier.weight(1f).fillMaxSize(),
                    )
                }
            }
            else -> Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    MultiviewGridCell(tiles.getOrNull(0), audioFocusChannelId, onTapTile, onRemoveTile, Modifier.weight(1f).fillMaxSize())
                    MultiviewGridCell(tiles.getOrNull(1), audioFocusChannelId, onTapTile, onRemoveTile, Modifier.weight(1f).fillMaxSize())
                }
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    MultiviewGridCell(tiles.getOrNull(2), audioFocusChannelId, onTapTile, onRemoveTile, Modifier.weight(1f).fillMaxSize())
                    MultiviewGridCell(tiles.getOrNull(3), audioFocusChannelId, onTapTile, onRemoveTile, Modifier.weight(1f).fillMaxSize())
                }
            }
        }

        // Below the grid, not layered on top of it - the streams themselves are the point, and a
        // persistent bottom bar (rather than a menu you have to summon) is what actually makes
        // "add another stream" or "swap out the one I'm bored of" discoverable mid-session.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Palette.Surface)
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MultiviewToolbarAction(
                label = "Add Channel",
                enabled = tiles.size < MAX_MULTIVIEW_TILES,
                onClick = { showAddChannelPicker = true },
            )
            MultiviewToolbarAction(
                label = "Dismiss Focused",
                enabled = audioFocusChannelId != null,
                onClick = { audioFocusChannelId?.let(onRemoveTile) },
            )
        }
    }

    if (showAddChannelPicker) {
        MultiviewAddChannelPicker(
            candidates = pickerCandidates.filterNot { candidate -> tiles.any { it.channel.id == candidate.id } },
            onPick = { channel ->
                onAddChannel(channel)
                showAddChannelPicker = false
            },
            onDismiss = { showAddChannelPicker = false },
        )
    }
}

@Composable
private fun MultiviewToolbarAction(label: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    BasicText(
        text = label,
        style = TextStyle(color = if (enabled) Color.White else Color.White.copy(alpha = 0.35f), fontSize = 13.sp),
        modifier = modifier
            .background(Palette.SurfaceElevated, RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

/** Recent + favourite channels rather than a full category browser - see LiveTvViewModel.multiviewPickerCandidates' own comment for why. */
@Composable
private fun MultiviewAddChannelPicker(candidates: List<IptvChannelInfo>, onPick: (IptvChannelInfo) -> Unit, onDismiss: () -> Unit) {
    Popup(alignment = Alignment.Center, onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .widthIn(min = 220.dp, max = 300.dp)
                .contextMenuSurface(AppShapes.medium)
                .padding(vertical = 8.dp),
        ) {
            BasicText(
                text = "Add to Multiview",
                style = TextStyle(color = Palette.ContextMenuText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (candidates.isEmpty()) {
                BasicText(
                    text = "No recent or favourite channels to suggest - favourite a channel, or add one from the channel list instead.",
                    style = TextStyle(color = Palette.ContextMenuText.copy(alpha = 0.6f), fontSize = 13.sp),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            } else {
                Column(modifier = Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
                    candidates.forEach { channel ->
                        BasicText(
                            text = channel.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = TextStyle(color = Palette.ContextMenuText, fontSize = 15.sp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(channel) }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MultiviewGridCell(
    tile: MultiviewTile?,
    audioFocusChannelId: String?,
    onTapTile: (channelId: String) -> Unit,
    onRemoveTile: (channelId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (tile == null) {
        // A blank slot rather than stretching the other tiles to fill it - staying an empty
        // rectangle in the same position it'd occupy once a 4th channel is added keeps the grid
        // from visibly jumping around every time a tile count changes.
        Box(modifier = modifier.background(Palette.Surface))
    } else {
        MultiviewTileView(
            tile = tile,
            isFocused = tile.channel.id == audioFocusChannelId,
            onTap = { onTapTile(tile.channel.id) },
            onRemove = { onRemoveTile(tile.channel.id) },
            modifier = modifier,
        )
    }
}

@Composable
private fun MultiviewTileView(
    tile: MultiviewTile,
    isFocused: Boolean,
    onTap: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by tile.controller.uiState.collectAsStateWithLifecycle()
    Box(
        modifier = modifier
            .padding(1.dp)
            .background(Color.Black)
            .border(width = if (isFocused) 3.dp else 0.dp, color = Palette.Accent)
            .clickable(onClick = onTap),
    ) {
        VideoSurface(exoPlayer = tile.controller.exoPlayer, aspectMode = VideoAspectMode.FIT, modifier = Modifier.fillMaxSize())

        BasicText(
            text = tile.channel.name,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(color = Color.White, fontSize = 12.sp),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(horizontal = 6.dp, vertical = 3.dp),
        )

        IconButton(onClick = onRemove, modifier = Modifier.align(Alignment.TopEnd).size(32.dp)) {
            Icon(Icons.Filled.Close, contentDescription = "Remove from multiview", tint = Color.White)
        }

        if (uiState.isBuffering) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center).size(24.dp), color = Color.White)
        }
    }
}

/** Only ever shown once 2+ channels are staged (the caller checks this) - opens the grid built from whatever's already been added via long-press "Add to Multiview". */
@Composable
fun MultiviewButton(tileCount: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    BasicText(
        text = "Multiview ($tileCount)",
        style = TextStyle(color = Color.White, fontSize = 13.sp),
        modifier = modifier
            .background(Palette.Accent, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}
