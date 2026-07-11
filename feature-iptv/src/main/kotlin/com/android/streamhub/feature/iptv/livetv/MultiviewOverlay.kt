package com.android.streamhub.feature.iptv.livetv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.android.streamhub.core.design.AppShapes
import com.android.streamhub.core.design.Palette
import com.android.streamhub.core.player.KeepScreenOnWhilePlaying
import com.android.streamhub.core.player.VideoAspectMode
import com.android.streamhub.core.player.VideoSurface
import com.android.streamhub.feature.iptv.data.IptvCategoryInfo
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
    categories: List<IptvCategoryInfo>,
    pickerActiveTab: MultiviewPickerTab,
    recentChannels: List<IptvChannelInfo>,
    pickerChannels: List<IptvChannelInfo>,
    isLoadingPickerChannels: Boolean,
    onSelectPickerTab: (MultiviewPickerTab) -> Unit,
    onResetPicker: () -> Unit,
    onTapTile: (channelId: String) -> Unit,
    onRemoveTile: (channelId: String) -> Unit,
    onAddChannel: (IptvChannelInfo) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showAddChannelPicker by remember { mutableStateOf(false) }

    KeepScreenOnWhilePlaying(isPlaying = tiles.isNotEmpty())

    // Closes the channel picker first if it's open (same "dismiss the topmost thing" convention
    // as everywhere else in this app a sheet/dialog sits over a screen), only closing multiview
    // itself once there's nothing left on top - same underlying gap as LiveFullscreenOverlay's own
    // BackHandler: without this, Back here had nothing to catch it at all.
    BackHandler(onBack = { if (showAddChannelPicker) showAddChannelPicker = false else onClose() })

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
                onClick = {
                    onResetPicker()
                    showAddChannelPicker = true
                },
            )
            MultiviewToolbarAction(
                label = "Dismiss Focused",
                enabled = audioFocusChannelId != null,
                onClick = { audioFocusChannelId?.let(onRemoveTile) },
            )
        }
    }

    if (showAddChannelPicker) {
        MultiviewChannelPicker(
            categories = categories,
            activeTab = pickerActiveTab,
            recentChannels = recentChannels,
            tabChannels = pickerChannels,
            isLoadingTabChannels = isLoadingPickerChannels,
            excludeChannelIds = tiles.map { it.channel.id }.toSet(),
            onSelectTab = onSelectPickerTab,
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

/**
 * Category-tabbed channel browser docked to the bottom of the screen, modelled on Sparkle's own
 * multiview channel selector - Recent/Favourites/All Channels quick tabs plus every real live
 * category, each opening a horizontally-scrolling strip of thumbnail cards, rather than the small
 * recent+favourites-only popup this replaced. Shared by both entry points - the fullscreen
 * player's own "Multiview" button (starting a new session) and the grid's own "Add Channel"
 * toolbar action (extending one already running) - same picker either way. Backed by
 * LiveTvViewModel's own picker state (see MultiviewPickerTab) rather than the main screen's
 * selectedCategory/channels, so browsing here never leaves the main screen on a different
 * category underneath once the picker closes.
 */
@Composable
fun MultiviewChannelPicker(
    categories: List<IptvCategoryInfo>,
    activeTab: MultiviewPickerTab,
    recentChannels: List<IptvChannelInfo>,
    tabChannels: List<IptvChannelInfo>,
    isLoadingTabChannels: Boolean,
    excludeChannelIds: Set<String>,
    onSelectTab: (MultiviewPickerTab) -> Unit,
    onPick: (IptvChannelInfo) -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .pointerInput(Unit) { detectTapGestures { onDismiss() } },
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Palette.Surface)
                // Swallows taps so tapping the panel itself doesn't fall through to the scrim
                // behind it and dismiss the picker.
                .pointerInput(Unit) { detectTapGestures {} }
                .navigationBarsPadding()
                .padding(vertical = 10.dp),
        ) {
            LazyRow(modifier = Modifier.fillMaxWidth()) {
                item { PickerTabChip("Recent", activeTab is MultiviewPickerTab.Recent) { onSelectTab(MultiviewPickerTab.Recent) } }
                item { PickerTabChip("All Channels", activeTab is MultiviewPickerTab.AllChannels) { onSelectTab(MultiviewPickerTab.AllChannels) } }
                item { PickerTabChip("Favourites", activeTab is MultiviewPickerTab.Favorites) { onSelectTab(MultiviewPickerTab.Favorites) } }
                items(categories, key = { it.id }) { category ->
                    val selected = (activeTab as? MultiviewPickerTab.Category)?.category?.id == category.id
                    PickerTabChip(category.name, selected) { onSelectTab(MultiviewPickerTab.Category(category)) }
                }
            }

            val displayedChannels = (if (activeTab is MultiviewPickerTab.Recent) recentChannels else tabChannels)
                .filterNot { it.id in excludeChannelIds }

            Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                when {
                    activeTab !is MultiviewPickerTab.Recent && isLoadingTabChannels ->
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(28.dp))
                    displayedChannels.isEmpty() ->
                        BasicText(
                            text = "No channels here yet.",
                            style = TextStyle(color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp),
                        )
                    else -> LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                    ) {
                        items(displayedChannels, key = { it.id }) { channel ->
                            PickerChannelCard(channel = channel, onClick = { onPick(channel) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PickerTabChip(label: String, selected: Boolean, onClick: () -> Unit) {
    BasicText(
        text = label,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = TextStyle(
            color = if (selected) Color.White else Color.White.copy(alpha = 0.6f),
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        ),
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    )
}

@Composable
private fun PickerChannelCard(channel: IptvChannelInfo, onClick: () -> Unit) {
    Column(
        modifier = Modifier.padding(end = 10.dp).width(84.dp).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(AppShapes.small)
                .background(Color.White.copy(alpha = 0.08f)),
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
                BasicText(text = channel.name.take(2).uppercase(), style = TextStyle(color = Color.White, fontSize = 13.sp))
            }
        }
        BasicText(
            text = channel.name,
            style = TextStyle(color = Color.White, fontSize = 11.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 3.dp),
        )
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MultiviewTileView(
    tile: MultiviewTile,
    isFocused: Boolean,
    onTap: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by tile.controller.uiState.collectAsStateWithLifecycle()
    var showCloseMenu by remember { mutableStateOf(false) }
    // Closing the popup disposes whichever item currently holds D-pad focus with nothing left to
    // take over, so without this TV's focus system falls back to its own default target (the nav
    // rail, or whatever else is focusable) instead of landing back on this tile.
    val tileFocusRequester = remember { FocusRequester() }
    val closeMenu: () -> Unit = { showCloseMenu = false; runCatching { tileFocusRequester.requestFocus() } }
    Box(
        modifier = modifier
            .padding(1.dp)
            .background(Color.Black)
            .border(width = if (isFocused) 3.dp else 0.dp, color = Palette.Accent)
            .focusRequester(tileFocusRequester)
            // Tap gives this tile audio focus (the common case, done often); long-press offers to
            // close it instead - two different gestures for two very different-weight actions,
            // same reasoning as everywhere else in this app that a long-press opens a menu rather
            // than acting immediately.
            .combinedClickable(onClick = onTap, onLongClick = { showCloseMenu = true }),
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

    if (showCloseMenu) {
        // A plain Popup never moves D-pad focus into itself on TV - without this, the remote's
        // presses kept hitting the tile underneath, not this menu.
        val firstItemFocusRequester = remember { FocusRequester() }
        LaunchedEffect(Unit) { firstItemFocusRequester.requestFocus() }
        Popup(alignment = Alignment.Center, onDismissRequest = closeMenu) {
            Column(modifier = Modifier.contextMenuSurface(AppShapes.small).padding(vertical = 4.dp)) {
                BasicText(
                    text = "Close Stream",
                    style = TextStyle(color = Palette.ContextMenuText, fontSize = 15.sp, fontWeight = FontWeight.Medium),
                    modifier = Modifier
                        .focusRequester(firstItemFocusRequester)
                        .clickable {
                            onRemove()
                            closeMenu()
                        }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                )
            }
        }
    }
}
