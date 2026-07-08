package com.android.streamhub.feature.iptv.livetv

import android.content.res.Configuration
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.android.streamhub.feature.iptv.data.EpgProgram
import com.android.streamhub.feature.iptv.data.IptvCategoryInfo
import com.android.streamhub.feature.iptv.data.IptvChannelInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveTvScreenPhone(
    paddingValues: PaddingValues,
    onSettingsClick: () -> Unit,
    onFullscreen: (channelId: String) -> Unit,
    viewModel: LiveTvViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val miniPlayerState by viewModel.miniPlayerUiState.collectAsStateWithLifecycle()
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Resumes the focused channel whenever this screen enters composition (first time, or
    // returning from another tab/fullscreen) and pauses it whenever it leaves - there's no
    // reason to keep a channel the user can't see decoding/buffering in the background.
    // Category/channel browsing happens entirely within this same composable, so it never
    // triggers this - only genuinely navigating away from Live TV does.
    DisposableEffect(Unit) {
        viewModel.resumeMiniPlayer()
        onDispose { viewModel.pauseMiniPlayer() }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            TopAppBar(
                title = { Text("Live TV") },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Filled.Settings, contentDescription = "IPTV settings")
                    }
                },
                modifier = Modifier.statusBarsPadding(),
            )

            uiState.errorMessage?.let { error ->
                Text(text = error, color = Color.Red, modifier = Modifier.fillMaxWidth().padding(16.dp, 4.dp))
            }

            if (!uiState.hasSource) {
                AddSourcePrompt(onSetupClick = onSettingsClick, modifier = Modifier.weight(1f))
                return@Column
            }

            if (isLandscape) {
                Row(modifier = Modifier.fillMaxWidth().height(140.dp)) {
                    MiniPlayerPreview(
                        exoPlayer = viewModel.miniPlayerController.exoPlayer,
                        uiState = miniPlayerState,
                        onToggleMute = viewModel::toggleMiniPlayerMute,
                        onFullscreen = { uiState.focusedChannel?.let { onFullscreen(it.id) } },
                        modifier = Modifier.width(220.dp).fillMaxHeight(),
                    )
                    EpgInfoPanel(
                        channelName = uiState.focusedChannel?.name,
                        nowProgram = uiState.nowProgram,
                        nextProgram = uiState.nextProgram,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            } else {
                MiniPlayerPreview(
                    exoPlayer = viewModel.miniPlayerController.exoPlayer,
                    uiState = miniPlayerState,
                    onToggleMute = viewModel::toggleMiniPlayerMute,
                    onFullscreen = { uiState.focusedChannel?.let { onFullscreen(it.id) } },
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                )
                EpgInfoPanel(
                    channelName = uiState.focusedChannel?.name,
                    nowProgram = uiState.nowProgram,
                    nextProgram = uiState.nextProgram,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black)
                        .padding(12.dp, 8.dp),
                )
            }

            LiveTvBrowseContent(
                uiState = uiState,
                isLandscape = isLandscape,
                onSelectCategory = viewModel::selectCategory,
                onBackToCategories = viewModel::clearCategorySelection,
                onFocusChannel = viewModel::focusChannel,
                onPlayFullscreen = onFullscreen,
                onToggleFavorite = viewModel::toggleFavorite,
                onScheduleRecording = viewModel::scheduleRecording,
                onScheduleReminder = viewModel::scheduleReminder,
                // Without this, this content competes for height with the fixed-size mini-player
                // row/header above it under Column's default (unbounded) child measurement,
                // which is what made the grid/list silently fail to render in landscape - there's
                // much less vertical room there than portrait, so the mis-measurement was far
                // more visible even though the same bug existed in both orientations.
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun AddSourcePrompt(onSetupClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("No playlist added yet", color = Color.White)
            Text(
                text = "Add an Xtream Codes or M3U playlist to start watching Live TV.",
                color = Color.Gray,
                modifier = Modifier.padding(top = 8.dp, start = 32.dp, end = 32.dp),
            )
            IconButton(onClick = onSetupClick, modifier = Modifier.padding(top = 16.dp)) {
                Icon(Icons.Filled.Settings, contentDescription = "Add playlist")
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LiveTvBrowseContent(
    uiState: LiveTvUiState,
    isLandscape: Boolean,
    onSelectCategory: (IptvCategoryInfo) -> Unit,
    onBackToCategories: () -> Unit,
    onFocusChannel: (IptvChannelInfo) -> Unit,
    onPlayFullscreen: (channelId: String) -> Unit,
    onToggleFavorite: (IptvChannelInfo) -> Unit,
    onScheduleRecording: (channel: IptvChannelInfo, program: EpgProgram, startAdjustMinutes: Int, endAdjustMinutes: Int) -> Unit,
    onScheduleReminder: (channel: IptvChannelInfo, program: EpgProgram, leadMinutes: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedCategory = uiState.selectedCategory
    var selectedPrefix by remember { mutableStateOf<String?>(null) }
    var contextMenuChannel by remember { mutableStateOf<IptvChannelInfo?>(null) }

    Box(modifier = modifier.fillMaxWidth()) {
        when {
            selectedCategory == null && uiState.isLoadingCategories ->
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))

            selectedCategory == null -> Column(modifier = Modifier.fillMaxSize()) {
                ListItem(
                    headlineContent = { Text("Favourites") },
                    leadingContent = { Icon(Icons.Filled.Star, contentDescription = null) },
                    modifier = Modifier.clickable { onSelectCategory(LiveTvViewModel.FAVORITES_CATEGORY) },
                )
                CategoryPrefixFilterRow(
                    categories = uiState.categories,
                    selectedPrefix = selectedPrefix,
                    onSelectPrefix = { selectedPrefix = it },
                )
                val visibleCategories = selectedPrefix?.let { prefix ->
                    uiState.categories.filter { categoryPrefix(it.name) == prefix }
                } ?: uiState.categories
                // weight(1f) is load-bearing here, not decorative - see the comment on the
                // caller's modifier param above.
                LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    items(visibleCategories, key = { it.id }) { category ->
                        ListItem(
                            headlineContent = { Text(category.name) },
                            modifier = Modifier.clickable { onSelectCategory(category) },
                        )
                    }
                }
            }

            else -> Column(modifier = Modifier.fillMaxSize()) {
                ListItem(
                    headlineContent = { Text(selectedCategory.name) },
                    leadingContent = {
                        IconButton(onClick = onBackToCategories) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to categories")
                        }
                    },
                )
                when {
                    uiState.isLoadingChannels -> Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                    // EPG only appears in landscape, inline - not as a separate screen.
                    isLandscape -> EpgGridPanel(
                        channels = uiState.channels,
                        programmesByChannel = uiState.programmesByChannel,
                        windowStart = uiState.gridWindowStart,
                        windowEnd = uiState.gridWindowEnd,
                        isLoading = uiState.isLoadingEpgGrid,
                        loadProgress = uiState.epgGridLoadProgress,
                        onFocusChannel = onFocusChannel,
                        onScheduleRecording = onScheduleRecording,
                        onScheduleReminder = onScheduleReminder,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    )
                    else -> LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        items(uiState.channels, key = { it.id }) { channel ->
                            val isFavorite = channel.id in uiState.favoriteChannelIds
                            Box {
                                ListItem(
                                    headlineContent = { Text(channel.name) },
                                    leadingContent = channel.logoUrl?.let { url ->
                                        { AsyncImage(model = url, contentDescription = null, modifier = Modifier.size(40.dp)) }
                                    },
                                    trailingContent = if (isFavorite) {
                                        { Icon(Icons.Filled.Favorite, contentDescription = "Favourite", tint = Color(0xFFE0245E)) }
                                    } else {
                                        null
                                    },
                                    modifier = Modifier.combinedClickable(
                                        onClick = { onFocusChannel(channel) },
                                        onLongClick = { contextMenuChannel = channel },
                                    ),
                                )
                                DropdownMenu(
                                    expanded = contextMenuChannel?.id == channel.id,
                                    onDismissRequest = { contextMenuChannel = null },
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Play") },
                                        leadingIcon = { Icon(Icons.Filled.PlayArrow, contentDescription = null) },
                                        onClick = {
                                            onFocusChannel(channel)
                                            onPlayFullscreen(channel.id)
                                            contextMenuChannel = null
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(if (isFavorite) "Remove from Favourites" else "Add to Favourites") },
                                        leadingIcon = {
                                            Icon(
                                                if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                                contentDescription = null,
                                            )
                                        },
                                        onClick = {
                                            onToggleFavorite(channel)
                                            contextMenuChannel = null
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
