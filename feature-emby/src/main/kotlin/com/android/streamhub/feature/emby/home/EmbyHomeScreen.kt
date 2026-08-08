package com.android.streamhub.feature.emby.home

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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.android.streamhub.core.ui.phone.theme.appColorScheme
import com.android.streamhub.feature.emby.data.EmbyItemInfo
import com.android.streamhub.feature.emby.data.EmbyItemType

// Shared across phone and TV rather than split - same reasoning as JellyfinHomeScreen: home's
// layout (a vertical stack of horizontal rows) doesn't differ by orientation or need a D-pad-
// specific structure, so a single implementation avoids duplicating near-identical code. Wraps its
// own MaterialTheme since it's reachable from the TV nav host too.

@Composable
fun EmbyHomeScreen(
    paddingValues: PaddingValues,
    onOpenLibrary: (libraryId: String, itemType: EmbyItemType) -> Unit,
    onOpenItem: (EmbyItemInfo) -> Unit,
    onSignInClick: () -> Unit,
    onOpenFavorites: () -> Unit,
    viewModel: EmbyHomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    MaterialTheme(colorScheme = appColorScheme()) {
        Surface(modifier = Modifier.fillMaxSize()) {
            // No title text - the bottom nav tab already says "Emby", so a second, bigger heading
            // right above it would be redundant. statusBarsPadding lives here unconditionally so
            // this screen still gets status bar clearance in phone landscape, where paddingValues
            // arrives zeroed (same reasoning as JellyfinHomeScreen).
            Column(modifier = Modifier.fillMaxSize().padding(paddingValues).statusBarsPadding()) {
                when {
                    !uiState.hasSource -> EmbyAddSourcePrompt(onSignInClick = onSignInClick, modifier = Modifier.weight(1f))
                    uiState.isLoading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    uiState.isEmpty -> EmbyEmptyState(modifier = Modifier.weight(1f))
                    else -> EmbyHomeContent(
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
private fun EmbyAddSourcePrompt(onSignInClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("No Emby server signed in", color = Palette.TextPrimary)
            Text(
                text = "Sign in to an Emby server to browse your library.",
                color = Palette.TextMuted,
                modifier = Modifier.padding(top = 8.dp, start = 32.dp, end = 32.dp),
            )
            Button(onClick = onSignInClick, modifier = Modifier.padding(top = 16.dp)) {
                Text("Sign In")
            }
        }
    }
}

@Composable
private fun EmbyEmptyState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(text = "No media found on this server yet.", color = Palette.TextMuted)
    }
}

@Composable
private fun EmbyHomeContent(
    uiState: EmbyHomeUiState,
    onOpenLibrary: (libraryId: String, itemType: EmbyItemType) -> Unit,
    onOpenItem: (EmbyItemInfo) -> Unit,
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
            item(key = "continue_watching") {
                EmbyItemRow(title = "Continue Watching", items = uiState.continueWatching, onOpenItem = onOpenItem)
            }
        }

        if (uiState.nextUp.isNotEmpty()) {
            item(key = "next_up") {
                EmbyItemRow(title = "Next Up", items = uiState.nextUp, onOpenItem = onOpenItem)
            }
        }

        if (uiState.favourites.isNotEmpty()) {
            item(key = "favourites") {
                EmbyItemRow(
                    title = "Favourites",
                    items = uiState.favourites,
                    onOpenItem = onOpenItem,
                    onHeaderClick = onOpenFavorites,
                )
            }
        }

        items(uiState.latestSections, key = { "library:${it.library.id}" }) { section ->
            EmbyItemRow(
                title = "Latest in ${section.library.name}",
                items = section.items,
                onOpenItem = onOpenItem,
                onHeaderClick = { onOpenLibrary(section.library.id, section.library.type.toItemType()) },
            )
        }
    }
}

@Composable
private fun EmbyItemRow(
    title: String,
    items: List<EmbyItemInfo>,
    onOpenItem: (EmbyItemInfo) -> Unit,
    // Non-null only for rows backed by a real library (the "Latest in X" rows) - Continue
    // Watching/Next Up are per-item queues with no library of their own to jump into. Tapping the
    // heading itself is the row's only navigation affordance this pass (no separate "See All"
    // tile - out of MVP scope), matching the plan's "tapping into a library section heading" callback.
    onHeaderClick: (() -> Unit)? = null,
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .let { if (onHeaderClick != null) it.clickable(onClick = onHeaderClick) else it }
                .padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            Text(text = title, color = Palette.TextPrimary)
            if (onHeaderClick != null) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Palette.TextMuted)
            }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(items, key = { it.id }) { item ->
                EmbyPoster(item = item, onClick = { onOpenItem(item) })
            }
        }
    }
}

/** Plain poster + caption - no badge/unwatched-count overlays (EmbyItemInfo carries no favorite/watched-count fields this pass, see the data model's own doc comment). */
@Composable
fun EmbyPoster(item: EmbyItemInfo, onClick: () -> Unit) {
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
            // For episodes (Continue Watching/Next Up rows) the show's name is far more useful at
            // a glance than the individual episode title - movies/series just show their own.
            text = item.seriesName ?: item.name,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = Palette.TextPrimary,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
