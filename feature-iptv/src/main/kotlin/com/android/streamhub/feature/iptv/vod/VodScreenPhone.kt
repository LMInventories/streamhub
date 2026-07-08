package com.android.streamhub.feature.iptv.vod

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VodScreenPhone(
    paddingValues: PaddingValues,
    onSettingsClick: () -> Unit,
    onOpenMovie: (itemId: String) -> Unit,
    onOpenShow: (seriesId: String) -> Unit,
    viewModel: VodViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedCategory = uiState.selectedCategory

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            TopAppBar(
                title = {
                    if (selectedCategory == null) {
                        ModeDropdown(mode = uiState.mode, onModeChange = viewModel::setMode)
                    } else {
                        Text("VOD")
                    }
                },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Filled.Settings, contentDescription = "IPTV settings")
                    }
                },
            )

            uiState.errorMessage?.let { error ->
                Text(text = error, color = Palette.Error, modifier = Modifier.fillMaxWidth().padding(16.dp, 4.dp))
            }

            // weight(1f) is load-bearing here, not decorative - without it this content competed
            // for height with TopAppBar under Column's default (unbounded) child measurement.
            when {
                !uiState.hasSource -> Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No playlist added yet", color = Palette.TextPrimary)
                        Text(
                            text = "Add an Xtream Codes playlist to browse movies and shows.",
                            color = Palette.TextMuted,
                            modifier = Modifier.padding(top = 8.dp, start = 32.dp, end = 32.dp),
                        )
                        IconButton(onClick = onSettingsClick, modifier = Modifier.padding(top = 16.dp)) {
                            Icon(Icons.Filled.Settings, contentDescription = "Add playlist")
                        }
                    }
                }

                !uiState.isSupported -> Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "VOD needs an Xtream Codes source. M3U playlists don't have a standard way to separate movies from live channels.",
                        color = Palette.TextMuted,
                        modifier = Modifier.padding(32.dp),
                    )
                }

                selectedCategory == null && uiState.isLoadingCategories ->
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }

                selectedCategory == null -> LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    items(uiState.categories, key = { it.id }) { category ->
                        ListItem(
                            headlineContent = { Text(category.name) },
                            modifier = Modifier.clickable { viewModel.selectCategory(category) },
                        )
                    }
                }

                else -> Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    ListItem(
                        headlineContent = { Text(selectedCategory.name) },
                        leadingContent = {
                            IconButton(onClick = viewModel::clearCategorySelection) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to categories")
                            }
                        },
                    )
                    if (uiState.isLoadingContent) {
                        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            modifier = Modifier.weight(1f).fillMaxWidth().padding(8.dp),
                        ) {
                            if (uiState.mode == VodMode.MOVIES) {
                                items(uiState.movies, key = { it.id }) { movie ->
                                    Poster(name = movie.name, posterUrl = movie.posterUrl, onClick = { onOpenMovie(movie.id) })
                                }
                            } else {
                                items(uiState.shows, key = { it.id }) { show ->
                                    Poster(name = show.name, posterUrl = show.posterUrl, onClick = { onOpenShow(show.id) })
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
private fun ModeDropdown(mode: VodMode, onModeChange: (VodMode) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier.clickable { expanded = true },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(if (mode == VodMode.MOVIES) "Movies" else "TV Shows")
            Icon(Icons.Filled.ArrowDropDown, contentDescription = "Switch between Movies and TV Shows")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("Movies") }, onClick = { onModeChange(VodMode.MOVIES); expanded = false })
            DropdownMenuItem(text = { Text("TV Shows") }, onClick = { onModeChange(VodMode.SHOWS); expanded = false })
        }
    }
}

@Composable
private fun Poster(name: String, posterUrl: String?, onClick: () -> Unit) {
    Column(modifier = Modifier.padding(4.dp).clickable(onClick = onClick)) {
        Box(
            modifier = Modifier
                .aspectRatio(2f / 3f)
                .fillMaxWidth()
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
        Text(
            text = name,
            maxLines = 2,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
