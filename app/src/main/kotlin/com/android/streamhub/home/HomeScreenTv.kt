package com.android.streamhub.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.android.streamhub.core.common.domain.PlaybackItem

@Composable
fun HomeScreenTv(
    onItemClick: (PlaybackItem) -> Unit,
    onSettingsClick: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.refresh() }

    Column(modifier = Modifier.fillMaxSize()) {
        Button(onClick = onSettingsClick, modifier = Modifier.padding(24.dp, 24.dp, 24.dp, 0.dp)) {
            Text("IPTV settings")
        }

        Box(modifier = Modifier.fillMaxSize()) {
            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 220.dp),
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                ) {
                    items(uiState.items, key = { it.id }) { item ->
                        Card(
                            onClick = { onItemClick(item) },
                            modifier = Modifier.padding(8.dp),
                        ) {
                            Column {
                                item.posterUrl?.let { url ->
                                    AsyncImage(
                                        model = url,
                                        contentDescription = null,
                                        modifier = Modifier.size(64.dp).padding(top = 8.dp),
                                    )
                                }
                                Text(text = item.title, modifier = Modifier.padding(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
