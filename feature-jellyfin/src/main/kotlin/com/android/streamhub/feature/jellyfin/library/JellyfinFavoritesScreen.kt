package com.android.streamhub.feature.jellyfin.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.streamhub.core.design.Palette
import com.android.streamhub.core.design.PillToggle
import com.android.streamhub.core.ui.phone.theme.appColorScheme
import com.android.streamhub.feature.jellyfin.data.JellyfinItemInfo
import com.android.streamhub.feature.jellyfin.data.JellyfinItemType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JellyfinFavoritesScreen(
    onBack: () -> Unit,
    onOpenItem: (JellyfinItemInfo) -> Unit,
    viewModel: JellyfinFavoritesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // 0 = Movies, 1 = TV Shows - a favourited season/episode/other item still reads as "part of a
    // TV show" rather than its own third category, matching how VOD's own Movies/TV Shows split
    // works.
    var modeIndex by remember { mutableIntStateOf(0) }
    val filteredItems = uiState.items.filter { item ->
        if (modeIndex == 0) item.type == JellyfinItemType.MOVIE else item.type != JellyfinItemType.MOVIE
    }

    MaterialTheme(colorScheme = appColorScheme()) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text("Favourites") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    modifier = Modifier.statusBarsPadding(),
                )

                PillToggle(
                    options = listOf("Movies", "TV Shows"),
                    selectedIndex = modeIndex,
                    onSelect = { modeIndex = it },
                    modifier = Modifier.padding(16.dp, 8.dp),
                )

                uiState.errorMessage?.let { error ->
                    Text(text = error, color = Palette.Error, modifier = Modifier.fillMaxWidth().padding(16.dp, 4.dp))
                }

                if (filteredItems.isEmpty() && !uiState.isLoading) {
                    val label = if (modeIndex == 0) "movies" else "TV shows"
                    Text(
                        text = "No favourite $label yet - tap the heart on anything you like to add it here.",
                        color = Palette.TextMuted,
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                    )
                }

                JellyfinItemGrid(
                    items = filteredItems,
                    isLoading = uiState.isLoading,
                    onLoadMore = viewModel::loadMore,
                    onOpenItem = onOpenItem,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
