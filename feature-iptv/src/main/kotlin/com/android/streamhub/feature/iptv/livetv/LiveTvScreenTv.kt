package com.android.streamhub.feature.iptv.livetv

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.Text
import com.android.streamhub.feature.iptv.data.IptvCategoryInfo

@Composable
fun LiveTvScreenTv(
    onSettingsClick: () -> Unit,
    onFullscreen: (channelId: String) -> Unit,
    viewModel: LiveTvViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val miniPlayerState by viewModel.miniPlayerUiState.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        if (!uiState.hasSource) {
            AddSourcePromptTv(onSetupClick = onSettingsClick, modifier = Modifier.weight(1f))
            return@Column
        }

        Row(modifier = Modifier.fillMaxWidth().height(180.dp).padding(24.dp, 24.dp, 24.dp, 0.dp)) {
            MiniPlayerPreview(
                exoPlayer = viewModel.miniPlayerController.exoPlayer,
                uiState = miniPlayerState,
                onToggleMute = viewModel::toggleMiniPlayerMute,
                onFullscreen = { uiState.focusedChannel?.let { onFullscreen(it.id) } },
                modifier = Modifier.width(280.dp).fillMaxHeight(),
            )
            EpgInfoPanel(
                channelName = uiState.focusedChannel?.name,
                nowProgram = uiState.nowProgram,
                nextProgram = uiState.nextProgram,
                modifier = Modifier.padding(16.dp),
            )
            Spacer(modifier = Modifier.weight(1f))
            Button(onClick = onSettingsClick) { Text("IPTV settings") }
        }

        uiState.errorMessage?.let { error ->
            Text(text = error, color = Color.Red, modifier = Modifier.padding(24.dp, 8.dp))
        }

        val selectedCategory = uiState.selectedCategory

        // weight(1f) here is load-bearing, not decorative - without it this competed for height
        // with the fixed-size header Row above under Column's default (unbounded) child
        // measurement, which is what made the grid silently fail to render.
        Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(24.dp)) {
            when {
                selectedCategory == null && uiState.isLoadingCategories ->
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))

                selectedCategory == null -> CategoryGrid(
                    categories = uiState.categories,
                    onSelectCategory = viewModel::selectCategory,
                )

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
                            loadProgress = uiState.epgGridLoadProgress,
                            onFocusChannel = viewModel::focusChannel,
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AddSourcePromptTv(onSetupClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("No playlist added yet")
            Text("Add an Xtream Codes or M3U playlist to start watching Live TV.", modifier = Modifier.padding(top = 8.dp))
            Button(onClick = onSetupClick, modifier = Modifier.padding(top = 16.dp)) {
                Text("Add playlist")
            }
        }
    }
}

@Composable
private fun CategoryGrid(categories: List<IptvCategoryInfo>, onSelectCategory: (IptvCategoryInfo) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 220.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(categories, key = { it.id }) { category ->
            Card(onClick = { onSelectCategory(category) }, modifier = Modifier.padding(8.dp)) {
                Text(text = category.name, modifier = Modifier.padding(16.dp))
            }
        }
    }
}
