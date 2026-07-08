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
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.streamhub.core.design.Palette
import com.android.streamhub.feature.jellyfin.data.JellyfinItemInfo

private val JellyfinFavoritesColorScheme = darkColorScheme(
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
fun JellyfinFavoritesScreen(
    onBack: () -> Unit,
    onOpenItem: (JellyfinItemInfo) -> Unit,
    viewModel: JellyfinFavoritesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    MaterialTheme(colorScheme = JellyfinFavoritesColorScheme) {
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

                uiState.errorMessage?.let { error ->
                    Text(text = error, color = Palette.Error, modifier = Modifier.fillMaxWidth().padding(16.dp, 4.dp))
                }

                if (uiState.items.isEmpty() && !uiState.isLoading) {
                    Text(
                        text = "No favourites yet - tap the heart on anything you like to add it here.",
                        color = Palette.TextMuted,
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                    )
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
