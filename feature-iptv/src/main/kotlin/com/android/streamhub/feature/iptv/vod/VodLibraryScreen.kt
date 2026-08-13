package com.android.streamhub.feature.iptv.vod

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.streamhub.core.design.Palette
import com.android.streamhub.core.ui.phone.theme.appColorScheme

/**
 * VOD's library screen - one per mode (Movies/TV Shows), reached by tapping a hero tile or "See
 * All" on VOD Home. Single unforked composable, not a Phone/Tv pair - same precedent as
 * JellyfinLibraryScreen (TopAppBar + FilterChip row + poster grid, wraps its own MaterialTheme,
 * registered identically in both nav hosts), which already proved this exact shape safe on TV
 * with no extra D-pad focus wrapping needed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VodLibraryScreen(
    onBack: () -> Unit,
    onOpenMovie: (itemId: String) -> Unit,
    onOpenShow: (seriesId: String) -> Unit,
    viewModel: VodLibraryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val title = if (uiState.mode == VodMode.MOVIES) "Movies" else "TV Shows"

    MaterialTheme(colorScheme = appColorScheme()) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text(title) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        GridDensityButton(gridColumns = uiState.gridColumns, onSelect = viewModel::setGridColumns)
                    },
                    modifier = Modifier.statusBarsPadding(),
                )

                // "All" plus one chip per Xtream category - this IS the "genre axis" this screen
                // offers, since categories are the only real grouping signal Xtream provides.
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    FilterChip(
                        selected = uiState.selectedCategoryId == null,
                        onClick = { viewModel.selectCategory(null) },
                        label = { Text("All") },
                    )
                    uiState.categories.forEach { category ->
                        FilterChip(
                            selected = uiState.selectedCategoryId == category.id,
                            onClick = { viewModel.selectCategory(category.id) },
                            label = { Text(category.name) },
                        )
                    }
                }

                uiState.errorMessage?.let { error ->
                    Text(text = error, color = Palette.Error, modifier = Modifier.fillMaxWidth().padding(16.dp, 4.dp))
                }

                if (uiState.isLoadingContent) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(uiState.gridColumns),
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
