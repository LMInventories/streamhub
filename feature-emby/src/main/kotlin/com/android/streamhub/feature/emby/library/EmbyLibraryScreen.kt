package com.android.streamhub.feature.emby.library

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.streamhub.core.design.Palette
import com.android.streamhub.core.ui.phone.theme.appColorScheme
import com.android.streamhub.feature.emby.data.EmbyItemInfo
import com.android.streamhub.feature.emby.data.EmbySortOption

// Same "shared, wraps own theme" reasoning as JellyfinLibraryScreen: one implementation serves
// both phone and TV nav hosts (no ...Tv sibling needed) since a top bar + poster grid needs no
// orientation- or D-pad-specific structure of its own - TV-appropriate focus handling is already
// pushed down into EmbyItemGrid's own poster cards. libraryId/itemType (the route's nav args)
// aren't composable parameters, same as JellyfinLibraryScreen - EmbyLibraryViewModel reads them
// straight off its Hilt-populated SavedStateHandle.

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmbyLibraryScreen(
    onBack: () -> Unit,
    onOpenItem: (EmbyItemInfo) -> Unit,
    viewModel: EmbyLibraryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    MaterialTheme(colorScheme = appColorScheme()) {
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

                if (uiState.availableGenres.isNotEmpty() || uiState.availableYears.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        if (uiState.availableGenres.isNotEmpty()) {
                            LibraryFilterMenuChip(
                                options = uiState.availableGenres,
                                selected = uiState.selectedGenre,
                                placeholderLabel = "Genre",
                                allLabel = "All Genres",
                                optionLabel = { it },
                                onSelect = viewModel::setGenre,
                            )
                        }
                        if (uiState.availableYears.isNotEmpty()) {
                            LibraryFilterMenuChip(
                                options = uiState.availableYears,
                                selected = uiState.selectedYear,
                                placeholderLabel = "Year",
                                allLabel = "All Years",
                                optionLabel = { it.toString() },
                                onSelect = viewModel::setYear,
                            )
                        }
                    }
                }

                uiState.errorMessage?.let { error ->
                    Text(text = error, color = Palette.Error, modifier = Modifier.fillMaxWidth().padding(16.dp, 4.dp))
                }

                EmbyItemGrid(
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
private fun SortMenuButton(selected: EmbySortOption, onSelect: (EmbySortOption) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    // Closing the menu disposes whichever item currently holds D-pad focus with nothing left to
    // take over, so without this TV's focus system falls back to its own default target (the nav
    // rail) instead of landing back on the button that opened the menu.
    val anchorFocusRequester = remember { FocusRequester() }
    val closeMenu: () -> Unit = { expanded = false; runCatching { anchorFocusRequester.requestFocus() } }
    Box {
        IconButton(onClick = { expanded = true }, modifier = Modifier.focusRequester(anchorFocusRequester)) {
            Icon(Icons.Filled.Sort, contentDescription = "Sort")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = closeMenu) {
            // A DropdownMenu never moves D-pad focus into itself on TV - without this, the
            // remote's presses kept hitting whatever was focused underneath, not this menu.
            val firstItemFocusRequester = remember { FocusRequester() }
            LaunchedEffect(Unit) { firstItemFocusRequester.requestFocus() }
            EmbySortOption.entries.forEachIndexed { index, option ->
                DropdownMenuItem(
                    text = { Text(option.label, color = if (option == selected) Palette.Accent else Palette.TextPrimary) },
                    modifier = if (index == 0) Modifier.focusRequester(firstItemFocusRequester) else Modifier,
                    onClick = { onSelect(option); closeMenu() },
                )
            }
        }
    }
}

/**
 * Single-select filter chip + anchored DropdownMenu, same TV-focus-safety shape as SortMenuButton
 * above (anchor FocusRequester restored on close, first menu item grabs focus on open - a plain
 * DropdownMenu never moves D-pad focus into itself). Generic over T (genre name String, year Int)
 * rather than two near-identical copies. [selected] null means "no filter" - shown as
 * [placeholderLabel] on the chip itself, with [allLabel] as the menu's own clearing entry. Mirrors
 * JellyfinLibraryScreen's own LibraryFilterMenuChip exactly - feature modules don't share code, so
 * this is duplicated rather than extracted into a shared module, same as SortMenuButton above it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> LibraryFilterMenuChip(
    options: List<T>,
    selected: T?,
    placeholderLabel: String,
    allLabel: String,
    optionLabel: (T) -> String,
    onSelect: (T?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val anchorFocusRequester = remember { FocusRequester() }
    val closeMenu: () -> Unit = { expanded = false; runCatching { anchorFocusRequester.requestFocus() } }
    Box {
        FilterChip(
            selected = selected != null,
            onClick = { expanded = true },
            label = { Text(selected?.let(optionLabel) ?: placeholderLabel) },
            modifier = Modifier.focusRequester(anchorFocusRequester),
        )
        DropdownMenu(expanded = expanded, onDismissRequest = closeMenu) {
            val firstItemFocusRequester = remember { FocusRequester() }
            LaunchedEffect(Unit) { firstItemFocusRequester.requestFocus() }
            DropdownMenuItem(
                text = { Text(allLabel, color = if (selected == null) Palette.Accent else Palette.TextPrimary) },
                modifier = Modifier.focusRequester(firstItemFocusRequester),
                onClick = { onSelect(null); closeMenu() },
            )
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option), color = if (option == selected) Palette.Accent else Palette.TextPrimary) },
                    onClick = { onSelect(option); closeMenu() },
                )
            }
        }
    }
}
