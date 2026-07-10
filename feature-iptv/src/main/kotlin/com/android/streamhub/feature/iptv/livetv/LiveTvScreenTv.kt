package com.android.streamhub.feature.iptv.livetv

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Videocam
import com.android.streamhub.feature.iptv.data.IptvCategoryInfo

@Composable
fun LiveTvScreenTv(
    onOpenRecordings: () -> Unit,
    viewModel: LiveTvViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val miniPlayerState by viewModel.miniPlayerUiState.collectAsStateWithLifecycle()
    val recentChannels by viewModel.recentChannels.collectAsStateWithLifecycle()
    val multiviewPickerCandidates by viewModel.multiviewPickerCandidates.collectAsStateWithLifecycle()

    // Resumes the focused channel whenever this screen enters composition (first time, or
    // returning from another tab) and pauses it whenever it leaves - there's no reason to keep a
    // channel the user can't see decoding/buffering in the background. Category/channel browsing
    // (including expanding/collapsing the in-place fullscreen overlay below) happens entirely
    // within this same composable, so it never triggers this - only genuinely navigating away
    // from Live TV does. exitFullscreen() here is a safety net so switching tabs while fullscreen
    // doesn't leave the app's own tab row permanently hidden.
    DisposableEffect(Unit) {
        viewModel.resumeMiniPlayer()
        onDispose {
            viewModel.pauseMiniPlayer()
            viewModel.exitFullscreen()
            // Same "safety net" reasoning as exitFullscreen() above - without this, leaving Live
            // TV entirely (a different tab-row tab) while multiview is open would leave
            // fullscreenOverlayState stuck active and the tab row permanently hidden.
            viewModel.closeMultiview()
        }
    }

    if (uiState.isMultiviewActive) {
        MultiviewOverlay(
            tiles = uiState.multiviewTiles,
            audioFocusChannelId = uiState.multiviewAudioFocusChannelId,
            pickerCandidates = multiviewPickerCandidates,
            onTapTile = viewModel::setMultiviewAudioFocus,
            onRemoveTile = viewModel::removeFromMultiview,
            onAddChannel = viewModel::addToMultiview,
            onClose = viewModel::closeMultiview,
            modifier = Modifier.fillMaxSize(),
        )
        return
    }

    val focusedChannel = uiState.focusedChannel
    if (uiState.isFullscreen && focusedChannel != null) {
        LiveFullscreenOverlay(
            exoPlayer = viewModel.miniPlayerController.exoPlayer,
            playerUiState = miniPlayerState,
            channel = focusedChannel,
            nowProgram = uiState.nowProgram,
            nextProgram = uiState.nextProgram,
            recentChannels = recentChannels,
            multiviewPickerCandidates = multiviewPickerCandidates,
            onSwitchChannel = viewModel::switchFullscreenChannel,
            onPlayPause = viewModel::toggleMiniPlayerPlayback,
            onToggleMute = viewModel::toggleMiniPlayerMute,
            onStartMultiview = viewModel::startMultiviewFromFullscreen,
            onCollapse = viewModel::exitFullscreen,
            modifier = Modifier.fillMaxSize(),
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (!uiState.hasSource) {
            AddSourcePromptTv(modifier = Modifier.weight(1f))
            return@Column
        }

        val previewSizeMultiplier = uiState.previewPlayerSize.multiplier
        Row(modifier = Modifier.fillMaxWidth().height(180.dp * previewSizeMultiplier).padding(24.dp, 24.dp, 24.dp, 0.dp)) {
            MiniPlayerPreview(
                exoPlayer = viewModel.miniPlayerController.exoPlayer,
                uiState = miniPlayerState,
                onTap = viewModel::enterFullscreen,
                modifier = Modifier.width(280.dp * previewSizeMultiplier).fillMaxHeight(),
            )
            EpgInfoPanel(
                channelName = uiState.focusedChannel?.name,
                nowProgram = uiState.nowProgram,
                nextProgram = uiState.nextProgram,
                modifier = Modifier.padding(16.dp),
            )
        }

        uiState.errorMessage?.let { error ->
            Text(text = error, color = Color.Red, modifier = Modifier.padding(24.dp, 8.dp))
        }

        val selectedCategory = uiState.selectedCategory
        var selectedPrefix by remember { mutableStateOf<String?>(null) }

        // weight(1f) here is load-bearing, not decorative - without it this competed for height
        // with the fixed-size header Row above under Column's default (unbounded) child
        // measurement, which is what made the grid silently fail to render.
        Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(24.dp)) {
            when {
                selectedCategory == null && uiState.isLoadingCategories ->
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))

                selectedCategory == null -> Column(modifier = Modifier.fillMaxSize()) {
                    Row {
                        Button(onClick = { viewModel.selectCategory(LiveTvViewModel.FAVORITES_CATEGORY) }) {
                            Icon(Icons.Filled.Star, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                            Text("Favourites")
                        }
                        Button(onClick = onOpenRecordings, modifier = Modifier.padding(start = 12.dp)) {
                            Icon(Icons.Filled.Videocam, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                            Text("Recordings")
                        }
                    }
                    CategoryPrefixFilterRow(
                        categories = uiState.categories,
                        selectedPrefix = selectedPrefix,
                        onSelectPrefix = { selectedPrefix = it },
                    )
                    val visibleCategories = selectedPrefix?.let { prefix ->
                        uiState.categories.filter { categoryPrefix(it.name) == prefix }
                    } ?: uiState.categories
                    CategoryGrid(
                        categories = visibleCategories,
                        onSelectCategory = viewModel::selectCategory,
                        // Load-bearing, not decorative - see the comment on the outer Box above.
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    )
                }

                else -> Column(modifier = Modifier.fillMaxSize()) {
                    Button(onClick = viewModel::clearCategorySelection) {
                        Text("< ${selectedCategory.name}")
                    }
                    if (uiState.isLoadingChannels) {
                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                        }
                    } else {
                        // TV is always landscape-shaped, so the EPG grid replaces the plain
                        // channel list here unconditionally, rather than branching on
                        // orientation the way the phone screen does.
                        EpgGridPanel(
                            channels = uiState.channels,
                            programmesByChannel = uiState.programmesByChannel,
                            windowStart = uiState.gridWindowStart,
                            windowEnd = uiState.gridWindowEnd,
                            isLoading = uiState.isLoadingEpgGrid,
                            loadProgress = uiState.epgGridLoadProgress,
                            onFocusChannel = viewModel::focusChannel,
                            onScheduleRecording = viewModel::scheduleRecording,
                            onScheduleReminder = viewModel::scheduleReminder,
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AddSourcePromptTv(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("No playlist added yet")
            Text(
                "Add an Xtream Codes or M3U playlist from the Settings tab to start watching Live TV.",
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun CategoryGrid(
    categories: List<IptvCategoryInfo>,
    onSelectCategory: (IptvCategoryInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 220.dp),
        modifier = modifier,
    ) {
        items(categories, key = { it.id }) { category ->
            Card(onClick = { onSelectCategory(category) }, modifier = Modifier.padding(8.dp)) {
                Text(text = category.name, modifier = Modifier.padding(16.dp))
            }
        }
    }
}
