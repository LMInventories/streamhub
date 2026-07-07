package com.android.streamhub.feature.iptv.livetv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.android.streamhub.feature.iptv.data.IptvChannelInfo
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.Text

@Composable
fun LiveTvScreenTv(
    onSettingsClick: () -> Unit,
    onFullscreen: (channelId: String) -> Unit,
    onOpenGuide: (categoryId: String) -> Unit,
    viewModel: LiveTvViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val miniPlayerState by viewModel.miniPlayerUiState.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
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

        Box(modifier = Modifier.fillMaxSize().padding(24.dp)) {
            when {
                selectedCategory == null && uiState.isLoadingCategories ->
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))

                selectedCategory == null -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 220.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(uiState.categories, key = { it.id }) { category ->
                        Card(onClick = { viewModel.selectCategory(category) }, modifier = Modifier.padding(8.dp)) {
                            Text(text = category.name, modifier = Modifier.padding(16.dp))
                        }
                    }
                }

                else -> Column(modifier = Modifier.fillMaxSize()) {
                    Row {
                        Button(onClick = viewModel::clearCategorySelection) {
                            Text("< ${selectedCategory.name}")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = { onOpenGuide(selectedCategory.id) }) {
                            Text("7-day guide")
                        }
                    }
                    if (uiState.isLoadingChannels) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 220.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(uiState.channels, key = { it.id }) { channel ->
                                ChannelCard(channel = channel, onFocusChannel = viewModel::focusChannel)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelCard(channel: IptvChannelInfo, onFocusChannel: (IptvChannelInfo) -> Unit) {
    Card(
        onClick = { onFocusChannel(channel) },
        modifier = Modifier
            .padding(8.dp)
            .onFocusChanged { if (it.isFocused) onFocusChannel(channel) },
    ) {
        Column {
            channel.logoUrl?.let { url ->
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp).padding(top = 8.dp),
                )
            }
            Text(text = channel.name, modifier = Modifier.padding(16.dp))
        }
    }
}
