package com.android.streamhub.feature.iptv.livetv

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.CalendarViewWeek
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.android.streamhub.feature.iptv.data.IptvCategoryInfo
import com.android.streamhub.feature.iptv.data.IptvChannelInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveTvScreenPhone(
    paddingValues: PaddingValues,
    onSettingsClick: () -> Unit,
    onFullscreen: (channelId: String) -> Unit,
    onOpenGuide: (categoryId: String) -> Unit,
    viewModel: LiveTvViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val miniPlayerState by viewModel.miniPlayerUiState.collectAsStateWithLifecycle()
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

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
                onSelectCategory = viewModel::selectCategory,
                onBackToCategories = viewModel::clearCategorySelection,
                onFocusChannel = viewModel::focusChannel,
                onOpenGuide = onOpenGuide,
            )
        }
    }
}

@Composable
private fun LiveTvBrowseContent(
    uiState: LiveTvUiState,
    onSelectCategory: (IptvCategoryInfo) -> Unit,
    onBackToCategories: () -> Unit,
    onFocusChannel: (IptvChannelInfo) -> Unit,
    onOpenGuide: (categoryId: String) -> Unit,
) {
    val selectedCategory = uiState.selectedCategory

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            selectedCategory == null && uiState.isLoadingCategories ->
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))

            selectedCategory == null -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(uiState.categories, key = { it.id }) { category ->
                    ListItem(
                        headlineContent = { Text(category.name) },
                        modifier = Modifier.clickable { onSelectCategory(category) },
                    )
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
                    trailingContent = {
                        IconButton(onClick = { onOpenGuide(selectedCategory.id) }) {
                            Icon(Icons.Filled.CalendarViewWeek, contentDescription = "7-day guide")
                        }
                    },
                )
                if (uiState.isLoadingChannels) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(uiState.channels, key = { it.id }) { channel ->
                            ListItem(
                                headlineContent = { Text(channel.name) },
                                leadingContent = channel.logoUrl?.let { url ->
                                    { AsyncImage(model = url, contentDescription = null, modifier = Modifier.size(40.dp)) }
                                },
                                modifier = Modifier.clickable { onFocusChannel(channel) },
                            )
                        }
                    }
                }
            }
        }
    }
}
