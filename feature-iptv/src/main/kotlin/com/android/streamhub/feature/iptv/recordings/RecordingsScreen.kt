package com.android.streamhub.feature.iptv.recordings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.android.streamhub.core.design.AppShapes
import com.android.streamhub.core.design.Palette
import com.android.streamhub.core.design.tvFocusBorder
import com.android.streamhub.core.ui.phone.theme.appColorScheme
import com.android.streamhub.feature.iptv.data.scheduled.RecordedItemEntity
import com.android.streamhub.feature.iptv.livetv.dateTimeFormatter
import com.android.streamhub.feature.iptv.livetv.rememberUse24HourTime
import java.time.Instant
import java.time.ZoneId

// Shared across phone and TV rather than split, same reasoning as the Jellyfin screens - a poster
// grid doesn't need orientation-specific structure, and wraps its own MaterialTheme since it's
// reachable from the TV nav host too (tv-material3's ambient theme, not this one).

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingsScreen(
    onBack: () -> Unit,
    onPlayRecording: (itemId: String) -> Unit,
    viewModel: RecordingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var contextMenuItemId by remember { mutableStateOf<Long?>(null) }

    MaterialTheme(colorScheme = appColorScheme()) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text("Recordings") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    modifier = Modifier.statusBarsPadding(),
                )

                when {
                    uiState.isLoading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    uiState.recordings.isEmpty() -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("No recordings yet", color = Palette.TextPrimary)
                            Text(
                                text = "Long-press a programme in the EPG guide and choose Record to save it here.",
                                color = Palette.TextMuted,
                                modifier = Modifier.padding(top = 8.dp, start = 32.dp, end = 32.dp),
                            )
                        }
                    }
                    else -> LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.fillMaxSize().padding(8.dp),
                    ) {
                        items(uiState.recordings, key = { it.id }) { item ->
                            RecordingTile(
                                item = item,
                                showContextMenu = contextMenuItemId == item.id,
                                onClick = { onPlayRecording(item.id.toString()) },
                                onLongClick = { contextMenuItemId = item.id },
                                onDismissContextMenu = { contextMenuItemId = null },
                                onDelete = { viewModel.deleteRecording(item) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecordingTile(
    item: RecordedItemEntity,
    showContextMenu: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDismissContextMenu: () -> Unit,
    onDelete: () -> Unit,
) {
    val use24Hour = rememberUse24HourTime()
    val interactionSource = remember { MutableInteractionSource() }
    // Closing the context menu disposes whichever item currently holds D-pad focus with nothing
    // left to take over, so without this TV's focus system falls back to its own default target
    // (the nav rail) instead of landing back on the tile that opened the menu.
    val tileFocusRequester = remember { FocusRequester() }
    val restoreFocus: () -> Unit = { runCatching { tileFocusRequester.requestFocus() } }
    Box {
        Column(
            modifier = Modifier
                .padding(4.dp)
                .tvFocusBorder(interactionSource, AppShapes.small)
                .focusRequester(tileFocusRequester)
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
                    onClick = onClick,
                    onLongClick = onLongClick,
                ),
        ) {
            Box(
                modifier = Modifier
                    .aspectRatio(16f / 9f)
                    .fillMaxWidth()
                    .clip(AppShapes.small)
                    .background(Palette.Surface),
            ) {
                if (item.channelLogoUrl != null) {
                    AsyncImage(
                        model = item.channelLogoUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Videocam, contentDescription = null, tint = Palette.TextMuted)
                    }
                }
            }
            Text(
                text = item.programTitle,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = Palette.TextPrimary,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                text = "${item.channelName} · ${dateTimeFormatter(use24Hour).format(Instant.ofEpochSecond(item.recordedAtEpochSeconds).atZone(ZoneId.systemDefault()))}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = Palette.TextMuted,
                fontSize = 12.sp,
            )
        }
        DropdownMenu(
            expanded = showContextMenu,
            onDismissRequest = { onDismissContextMenu(); restoreFocus() },
            containerColor = Palette.ContextMenuSurface,
            shadowElevation = 10.dp,
        ) {
            // A DropdownMenu never moves D-pad focus into itself on TV - without this, the
            // remote's presses kept hitting whatever tile was focused underneath, not this menu.
            val firstItemFocusRequester = remember { FocusRequester() }
            LaunchedEffect(Unit) { firstItemFocusRequester.requestFocus() }
            DropdownMenuItem(
                text = { Text("Play", fontSize = 16.sp, color = Palette.ContextMenuText) },
                leadingIcon = { Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Palette.ContextMenuText) },
                modifier = Modifier.focusRequester(firstItemFocusRequester),
                onClick = {
                    onClick()
                    onDismissContextMenu()
                    restoreFocus()
                },
            )
            DropdownMenuItem(
                text = { Text("Delete", fontSize = 16.sp, color = Palette.ContextMenuText) },
                leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = Palette.ContextMenuText) },
                onClick = {
                    onDelete()
                    onDismissContextMenu()
                    restoreFocus()
                },
            )
        }
    }
}
