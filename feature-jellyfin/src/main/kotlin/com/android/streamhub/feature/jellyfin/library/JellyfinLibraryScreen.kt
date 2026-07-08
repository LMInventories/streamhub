package com.android.streamhub.feature.jellyfin.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.streamhub.core.design.Palette
import com.android.streamhub.feature.jellyfin.data.JellyfinItemInfo
import com.android.streamhub.feature.jellyfin.data.JellyfinSortOption

// Same "shared, wraps own theme" reasoning as JellyfinHomeScreen.
private val JellyfinLibraryColorScheme = darkColorScheme(
    primary = Palette.Accent,
    background = Palette.Background,
    onBackground = Palette.TextPrimary,
    surface = Palette.Surface,
    onSurface = Palette.TextPrimary,
    surfaceVariant = Palette.SurfaceElevated,
    onSurfaceVariant = Palette.TextMuted,
    outline = Palette.Border,
    error = Palette.Error,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JellyfinLibraryScreen(
    onBack: () -> Unit,
    onOpenItem: (JellyfinItemInfo) -> Unit,
    viewModel: JellyfinLibraryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    MaterialTheme(colorScheme = JellyfinLibraryColorScheme) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text(uiState.libraryName) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        SortMenuButton(selected = uiState.sortOption, onSelect = viewModel::setSortOption)
                    },
                    modifier = Modifier.statusBarsPadding(),
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    FilterChip(
                        selected = uiState.favoritesOnly,
                        onClick = { viewModel.setFavoritesOnly(!uiState.favoritesOnly) },
                        label = { Text("Favourites") },
                    )
                    FilterChip(
                        selected = uiState.unwatchedOnly,
                        onClick = { viewModel.setUnwatchedOnly(!uiState.unwatchedOnly) },
                        label = { Text("Unwatched") },
                    )
                }

                uiState.errorMessage?.let { error ->
                    Text(text = error, color = Palette.Error, modifier = Modifier.fillMaxWidth().padding(16.dp, 4.dp))
                }

                JellyfinItemGrid(
                    items = uiState.items,
                    isLoading = uiState.isLoading,
                    onLoadMore = viewModel::loadMore,
                    onOpenItem = onOpenItem,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SortMenuButton(selected: JellyfinSortOption, onSelect: (JellyfinSortOption) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.Sort, contentDescription = "Sort")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            JellyfinSortOption.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label, color = if (option == selected) Palette.Accent else Palette.TextPrimary) },
                    onClick = { onSelect(option); expanded = false },
                )
            }
        }
    }
}
