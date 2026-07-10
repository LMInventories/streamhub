package com.android.streamhub.feature.iptv.vod

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.android.streamhub.core.design.AppShapes
import com.android.streamhub.core.design.Palette
import com.android.streamhub.core.design.PillToggle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VodScreenPhone(
    paddingValues: PaddingValues,
    onOpenMovie: (itemId: String) -> Unit,
    onOpenShow: (seriesId: String) -> Unit,
    viewModel: VodViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Surface(modifier = Modifier.fillMaxSize()) {
        // statusBarsPadding lives here unconditionally, not just on the grid density row below -
        // that row only renders once a category's selected, but this screen still needs status
        // bar clearance in landscape (where paddingValues arrives zeroed - see PhoneScaffold)
        // even before that.
        VodBrowseContent(
            uiState = uiState,
            onSelectCategory = viewModel::selectCategory,
            onClearCategorySelection = viewModel::clearCategorySelection,
            onSetMode = viewModel::setMode,
            onSetGridColumns = viewModel::setGridColumns,
            onOpenMovie = onOpenMovie,
            onOpenShow = onOpenShow,
            modifier = Modifier.fillMaxSize().padding(paddingValues).statusBarsPadding(),
        )
    }
}

/**
 * Not private - reused as-is by VodScreenTv rather than a second TV-only implementation of the
 * same category/poster-grid browsing, same reasoning as LiveTvScreenPhone's LiveTvBrowseContent.
 */
@Composable
fun VodBrowseContent(
    uiState: VodUiState,
    onSelectCategory: (VodCategoryInfo) -> Unit,
    onClearCategorySelection: () -> Unit,
    onSetMode: (VodMode) -> Unit,
    onSetGridColumns: (Int) -> Unit,
    onOpenMovie: (itemId: String) -> Unit,
    onOpenShow: (seriesId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedCategory = uiState.selectedCategory

    Column(modifier = modifier) {
        // No title text here - the bottom nav tab/tab row is already labeled "VOD", so a second,
        // bigger "VOD" heading right above it was purely redundant. Just the grid density action,
        // right-aligned, rather than a full-height empty app bar - and only shown at all once
        // there's actually a category grid on screen for it to affect.
        if (selectedCategory != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, end = 4.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                GridDensityButton(gridColumns = uiState.gridColumns, onSelect = onSetGridColumns)
            }
        }

        if (selectedCategory == null && uiState.hasSource && uiState.isSupported) {
            ModePillToggle(
                mode = uiState.mode,
                onModeChange = onSetMode,
                modifier = Modifier.padding(16.dp, 8.dp),
            )
        }

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
                        text = "Add an Xtream Codes playlist from the Settings tab to browse movies and shows.",
                        color = Palette.TextMuted,
                        modifier = Modifier.padding(top = 8.dp, start = 32.dp, end = 32.dp),
                    )
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

            selectedCategory == null -> LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.weight(1f).fillMaxWidth().padding(8.dp),
            ) {
                items(uiState.categories, key = { it.id }) { category ->
                    CategoryTile(name = category.name, onClick = { onSelectCategory(category) })
                }
            }

            else -> Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
                ListItem(
                    headlineContent = { Text(selectedCategory.name) },
                    leadingContent = {
                        IconButton(onClick = onClearCategorySelection) {
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

@Composable
private fun GridDensityButton(gridColumns: Int, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.GridView, contentDescription = "Grid size ($gridColumns columns)")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            VOD_GRID_COLUMN_OPTIONS.forEach { count ->
                DropdownMenuItem(
                    text = { Text("$count columns") },
                    onClick = { onSelect(count); expanded = false },
                )
            }
        }
    }
}

@Composable
private fun ModePillToggle(mode: VodMode, onModeChange: (VodMode) -> Unit, modifier: Modifier = Modifier) {
    PillToggle(
        options = listOf("Movies", "TV Shows"),
        selectedIndex = if (mode == VodMode.MOVIES) 0 else 1,
        onSelect = { index -> onModeChange(if (index == 0) VodMode.MOVIES else VodMode.SHOWS) },
        modifier = modifier,
    )
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

// A row of plain text rows read as a bare list rather than something meant to be tapped, and
// didn't leave room for a category thumbnail if that data ever becomes available (Xtream doesn't
// send one today). A landscape tile - the same shape a category backdrop/thumbnail would actually
// use - reads as a real destination and is a drop-in spot for AsyncImage later, same as Poster
// above once that data exists.
@Composable
private fun CategoryTile(name: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(4.dp)
            .aspectRatio(16f / 9f)
            .fillMaxWidth()
            .clip(AppShapes.medium)
            .background(Palette.Surface)
            .clickable(onClick = onClick)
            .padding(12.dp),
        contentAlignment = Alignment.BottomStart,
    ) {
        Icon(
            Icons.Filled.Movie,
            contentDescription = null,
            tint = Palette.TextMuted.copy(alpha = 0.3f),
            modifier = Modifier.align(Alignment.Center).aspectRatio(1f).fillMaxWidth(0.4f),
        )
        Text(
            text = name,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = Palette.TextPrimary,
        )
    }
}
