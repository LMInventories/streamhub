package com.android.streamhub.feature.jellyfin.home

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.android.streamhub.feature.jellyfin.data.JellyfinItemInfo
import com.android.streamhub.feature.jellyfin.data.JellyfinLibraryInfo

// Shared across phone and TV rather than split like the Live TV/VOD browse screens - unlike
// those, home's layout (a vertical stack of horizontal rows) doesn't actually differ by
// orientation or need a D-pad-specific structure, so a single implementation avoids duplicating
// what would otherwise be near-identical code. Wraps its own MaterialTheme since it's reachable
// from the TV nav host too, same reasoning as ItemDetailScreen/IptvSettingsScreen.
private val JellyfinHomeColorScheme = darkColorScheme(
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
fun JellyfinHomeScreen(
    paddingValues: PaddingValues,
    onSettingsClick: () -> Unit,
    onOpenLibrary: (JellyfinLibraryInfo) -> Unit,
    onOpenItem: (JellyfinItemInfo) -> Unit,
    onOpenFavorites: () -> Unit,
    viewModel: JellyfinHomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    MaterialTheme(colorScheme = JellyfinHomeColorScheme) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                // No title text - the bottom nav tab below already says "Jellyfin", so a second,
                // bigger heading right above it was redundant. Just the settings action, right-aligned.
                Row(
                    modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(top = 8.dp, end = 4.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Filled.Settings, contentDescription = "Jellyfin settings")
                    }
                }

                when {
                    !uiState.hasSource -> JellyfinAddSourcePrompt(onSettingsClick, modifier = Modifier.weight(1f))
                    uiState.isLoading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    else -> JellyfinHomeContent(
                        uiState = uiState,
                        onOpenLibrary = onOpenLibrary,
                        onOpenItem = onOpenItem,
                        onOpenFavorites = onOpenFavorites,
                    )
                }
            }
        }
    }
}

@Composable
private fun JellyfinAddSourcePrompt(onSetupClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("No Jellyfin server signed in", color = Palette.TextPrimary)
            Text(
                text = "Sign in to a Jellyfin server to browse your library.",
                color = Palette.TextMuted,
                modifier = Modifier.padding(top = 8.dp, start = 32.dp, end = 32.dp),
            )
            IconButton(onClick = onSetupClick, modifier = Modifier.padding(top = 16.dp)) {
                Icon(Icons.Filled.Settings, contentDescription = "Sign in")
            }
        }
    }
}

@Composable
private fun JellyfinHomeContent(
    uiState: JellyfinHomeUiState,
    onOpenLibrary: (JellyfinLibraryInfo) -> Unit,
    onOpenItem: (JellyfinItemInfo) -> Unit,
    onOpenFavorites: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        uiState.errorMessage?.let { error ->
            item(key = "error") {
                Text(text = error, color = Palette.Error, modifier = Modifier.fillMaxWidth().padding(16.dp, 4.dp))
            }
        }

        if (uiState.continueWatching.isNotEmpty()) {
            item(key = "resume") {
                JellyfinItemRow(title = "Continue Watching", items = uiState.continueWatching, onOpenItem = onOpenItem)
            }
        }

        if (uiState.nextUp.isNotEmpty()) {
            item(key = "nextup") {
                JellyfinItemRow(title = "Next Up", items = uiState.nextUp, onOpenItem = onOpenItem)
            }
        }

        if (uiState.favorites.isNotEmpty()) {
            item(key = "favorites") {
                JellyfinItemRow(
                    title = "Favourites",
                    items = uiState.favorites,
                    onOpenItem = onOpenItem,
                    onSeeAll = onOpenFavorites,
                )
            }
        }

        items(uiState.libraries, key = { it.id }) { library ->
            val latest = uiState.latestByLibrary[library.id].orEmpty()
            if (latest.isNotEmpty()) {
                JellyfinItemRow(
                    title = "Latest in ${library.name}",
                    items = latest,
                    onOpenItem = onOpenItem,
                    onSeeAll = { onOpenLibrary(library) },
                )
            }
        }
    }
}

@Composable
private fun JellyfinItemRow(
    title: String,
    items: List<JellyfinItemInfo>,
    onOpenItem: (JellyfinItemInfo) -> Unit,
    onSeeAll: (() -> Unit)? = null,
) {
    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .let { if (onSeeAll != null) it.clickable(onClick = onSeeAll) else it },
        ) {
            Text(text = title, color = Palette.TextPrimary, modifier = Modifier.padding(bottom = 8.dp))
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(items, key = { it.id }) { item ->
                JellyfinPoster(item = item, onClick = { onOpenItem(item) })
            }
        }
    }
}

@Composable
private fun JellyfinPoster(item: JellyfinItemInfo, onClick: () -> Unit) {
    Column(modifier = Modifier.width(120.dp).clickable(onClick = onClick)) {
        Box(
            modifier = Modifier
                .aspectRatio(2f / 3f)
                .fillMaxWidth()
                .clip(AppShapes.small),
        ) {
            if (item.primaryImageUrl != null) {
                AsyncImage(
                    model = item.primaryImageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(modifier = Modifier.fillMaxSize().background(Palette.Surface))
            }
        }
        Text(
            // For episodes (Continue Watching/Next Up rows) the show's name is far more useful
            // at a glance than the individual episode title - movies/series just show their own.
            text = item.seriesName ?: item.name,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = Palette.TextPrimary,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
