package com.android.streamhub.feature.iptv.vod

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.android.streamhub.core.design.AppShapes
import com.android.streamhub.core.design.Palette
import com.android.streamhub.feature.iptv.data.VodCategoryInfo

@Composable
fun VodScreenTv(
    onSettingsClick: () -> Unit,
    onOpenMovie: (itemId: String) -> Unit,
    onOpenShow: (seriesId: String) -> Unit,
    viewModel: VodViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedCategory = uiState.selectedCategory

    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.padding(24.dp, 24.dp, 24.dp, 0.dp)) {
            if (selectedCategory == null) {
                Button(onClick = { viewModel.setMode(VodMode.MOVIES) }) {
                    Text(if (uiState.mode == VodMode.MOVIES) "> Movies" else "Movies")
                }
                Button(onClick = { viewModel.setMode(VodMode.SHOWS) }, modifier = Modifier.padding(start = 8.dp)) {
                    Text(if (uiState.mode == VodMode.SHOWS) "> TV Shows" else "TV Shows")
                }
            }
            Button(onClick = onSettingsClick, modifier = Modifier.padding(start = 8.dp)) {
                Text("IPTV settings")
            }
        }

        uiState.errorMessage?.let { error ->
            Text(text = error, color = Palette.Error, modifier = Modifier.padding(24.dp, 4.dp))
        }

        // weight(1f) is load-bearing here, not decorative - without it this competed for height
        // with the fixed-size header Row above under Column's default (unbounded) child
        // measurement.
        Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(24.dp)) {
            when {
                !uiState.hasSource -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No playlist added yet")
                        Text(
                            "Add an Xtream Codes playlist to browse movies and shows.",
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        Button(onClick = onSettingsClick, modifier = Modifier.padding(top = 16.dp)) {
                            Text("Add playlist")
                        }
                    }
                }

                !uiState.isSupported -> Text(
                    text = "VOD needs an Xtream Codes source. M3U playlists don't have a standard way to separate movies from live channels.",
                    color = Palette.TextMuted,
                )

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
                    if (uiState.isLoadingContent) {
                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 160.dp),
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                        ) {
                            if (uiState.mode == VodMode.MOVIES) {
                                items(uiState.movies, key = { it.id }) { movie ->
                                    PosterCard(name = movie.name, posterUrl = movie.posterUrl, onClick = { onOpenMovie(movie.id) })
                                }
                            } else {
                                items(uiState.shows, key = { it.id }) { show ->
                                    PosterCard(name = show.name, posterUrl = show.posterUrl, onClick = { onOpenShow(show.id) })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryGrid(categories: List<VodCategoryInfo>, onSelectCategory: (VodCategoryInfo) -> Unit) {
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

@Composable
private fun PosterCard(name: String, posterUrl: String?, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.padding(8.dp).width(140.dp)) {
        Column {
            Box(
                modifier = Modifier
                    .aspectRatio(2f / 3f)
                    .clip(AppShapes.small),
            ) {
                if (posterUrl != null) {
                    AsyncImage(
                        model = posterUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize().background(Palette.Surface))
                }
            }
            Text(text = name, maxLines = 2, modifier = Modifier.padding(8.dp))
        }
    }
}
