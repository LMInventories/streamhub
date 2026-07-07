package com.android.streamhub.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.android.streamhub.core.common.domain.PlaybackItem
import com.android.streamhub.core.design.Palette
import com.android.streamhub.core.design.SourceBadge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreenPhone(
    paddingValues: PaddingValues,
    onItemClick: (PlaybackItem) -> Unit,
    onSettingsClick: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.refresh() }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            TopAppBar(
                title = { Text("StreamHub") },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Filled.Settings, contentDescription = "IPTV settings")
                    }
                },
                modifier = Modifier.statusBarsPadding(),
            )

            uiState.sourceErrors.forEach { error ->
                Text(
                    text = error,
                    color = Palette.Error,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(uiState.items, key = { it.id }) { item ->
                            ListItem(
                                headlineContent = { Text(item.title) },
                                supportingContent = item.subtitle?.let { subtitle -> { Text(subtitle) } },
                                leadingContent = item.posterUrl?.let { url ->
                                    {
                                        AsyncImage(
                                            model = url,
                                            contentDescription = null,
                                            modifier = Modifier.size(48.dp),
                                        )
                                    }
                                },
                                trailingContent = { SourceBadge(item.sourceType) },
                                modifier = Modifier.clickable { onItemClick(item) },
                            )
                        }
                    }
                }
            }
        }
    }
}
