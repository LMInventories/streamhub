package com.android.streamhub.feature.iptv.vod

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.android.streamhub.core.design.AppShapes
import com.android.streamhub.core.design.Palette
import com.android.streamhub.feature.iptv.data.VodMovieInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VodScreenPhone(
    paddingValues: PaddingValues,
    onSettingsClick: () -> Unit,
    onFullscreen: (itemId: String) -> Unit,
    viewModel: VodViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedCategory = uiState.selectedCategory

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            TopAppBar(
                title = { Text("VOD") },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Filled.Settings, contentDescription = "IPTV settings")
                    }
                },
            )

            uiState.errorMessage?.let { error ->
                Text(text = error, color = Palette.Error, modifier = Modifier.fillMaxWidth().padding(16.dp, 4.dp))
            }

            when {
                !uiState.isSupported -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "VOD needs an Xtream Codes source. M3U playlists don't have a standard way to separate movies from live channels.",
                        color = Palette.TextMuted,
                        modifier = Modifier.padding(32.dp),
                    )
                }

                selectedCategory == null && uiState.isLoadingCategories ->
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }

                selectedCategory == null -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(uiState.categories, key = { it.id }) { category ->
                        ListItem(
                            headlineContent = { Text(category.name) },
                            modifier = Modifier.clickable { viewModel.selectCategory(category) },
                        )
                    }
                }

                else -> Column(modifier = Modifier.fillMaxSize()) {
                    ListItem(
                        headlineContent = { Text(selectedCategory.name) },
                        leadingContent = {
                            IconButton(onClick = viewModel::clearCategorySelection) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to categories")
                            }
                        },
                    )
                    if (uiState.isLoadingMovies) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            modifier = Modifier.fillMaxSize().padding(8.dp),
                        ) {
                            items(uiState.movies, key = { it.id }) { movie ->
                                MoviePoster(movie = movie, onClick = { onFullscreen(movie.id) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MoviePoster(movie: VodMovieInfo, onClick: () -> Unit) {
    Column(modifier = Modifier.padding(4.dp).clickable(onClick = onClick)) {
        Box(
            modifier = Modifier
                .aspectRatio(2f / 3f)
                .fillMaxWidth()
                .clip(AppShapes.small),
        ) {
            if (movie.posterUrl != null) {
                AsyncImage(
                    model = movie.posterUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(modifier = Modifier.fillMaxSize().background(Palette.Surface))
            }
        }
        Text(
            text = movie.name,
            maxLines = 2,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
