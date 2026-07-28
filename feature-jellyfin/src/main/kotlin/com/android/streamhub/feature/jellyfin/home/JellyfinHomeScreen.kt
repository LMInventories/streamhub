package com.android.streamhub.feature.jellyfin.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.android.streamhub.feature.jellyfin.data.JellyfinHomeSection
import com.android.streamhub.feature.jellyfin.data.JellyfinHomeSectionKeys
import com.android.streamhub.feature.jellyfin.data.JellyfinItemInfo
import com.android.streamhub.feature.jellyfin.data.JellyfinLibraryInfo

// Shared across phone and TV rather than split like the Live TV/VOD browse screens - unlike
// those, home's layout (a vertical stack of horizontal rows) doesn't actually differ by
// orientation or need a D-pad-specific structure, so a single implementation avoids duplicating
// what would otherwise be near-identical code. Wraps its own MaterialTheme since it's reachable
// from the TV nav host too, same reasoning as ItemDetailScreen/IptvSettingsScreen.

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JellyfinHomeScreen(
    paddingValues: PaddingValues,
    onOpenLibrary: (JellyfinLibraryInfo) -> Unit,
    onOpenItem: (JellyfinItemInfo) -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenSeeAll: (kind: String) -> Unit,
    viewModel: JellyfinHomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    MaterialTheme(colorScheme = appColorScheme()) {
        Surface(modifier = Modifier.fillMaxSize()) {
            // No title text - the bottom nav tab below already says "Jellyfin", so a second,
            // bigger heading right above it was redundant. statusBarsPadding lives here
            // unconditionally now that there's no settings row to carry it - this screen still
            // needs status bar clearance in phone landscape, where paddingValues arrives zeroed.
            Column(modifier = Modifier.fillMaxSize().padding(paddingValues).statusBarsPadding()) {
                when {
                    !uiState.hasSource -> JellyfinAddSourcePrompt(modifier = Modifier.weight(1f))
                    uiState.isLoading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    else -> JellyfinHomeContent(
                        uiState = uiState,
                        onOpenLibrary = onOpenLibrary,
                        onOpenItem = onOpenItem,
                        onOpenFavorites = onOpenFavorites,
                        onOpenSeeAll = onOpenSeeAll,
                    )
                }
            }
        }
    }
}

@Composable
private fun JellyfinAddSourcePrompt(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("No Jellyfin server signed in", color = Palette.TextPrimary)
            Text(
                text = "Sign in to a Jellyfin server from the Settings tab to browse your library.",
                color = Palette.TextMuted,
                modifier = Modifier.padding(top = 8.dp, start = 32.dp, end = 32.dp),
            )
        }
    }
}

@Composable
private fun JellyfinHomeContent(
    uiState: JellyfinHomeUiState,
    onOpenLibrary: (JellyfinLibraryInfo) -> Unit,
    onOpenItem: (JellyfinItemInfo) -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenSeeAll: (kind: String) -> Unit,
) {
    val librariesById = uiState.libraries.associateBy { it.id }

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

        item(key = "media") {
            val mediaEntries = uiState.sections.mapNotNull { section ->
                if (section.key == JellyfinHomeSectionKeys.CONTINUE_WATCHING || section.key == JellyfinHomeSectionKeys.NEXT_UP) return@mapNotNull null
                val action = sectionSeeAllAction(section, librariesById, onOpenFavorites, onOpenSeeAll, onOpenLibrary) ?: return@mapNotNull null
                MediaEntry(label = sectionMediaLabel(section, librariesById), onClick = action)
            }
            if (mediaEntries.isNotEmpty()) {
                JellyfinMediaRow(entries = mediaEntries)
            }
        }

        items(uiState.sections, key = { it.key }) { section ->
            // Each section only knows its own key/hasSeeAll (set once in the ViewModel, alongside
            // every other section's, regardless of user-chosen order) - mapping a key back to the
            // actual navigation callback stays here, since the ViewModel layer shouldn't carry
            // UI-bound lambdas.
            val onSeeAll = sectionSeeAllAction(section, librariesById, onOpenFavorites, onOpenSeeAll, onOpenLibrary)
            JellyfinItemRow(title = section.title, items = section.items, onOpenItem = onOpenItem, onSeeAll = onSeeAll)
        }
    }
}

private fun sectionSeeAllAction(
    section: JellyfinHomeSection,
    librariesById: Map<String, JellyfinLibraryInfo>,
    onOpenFavorites: () -> Unit,
    onOpenSeeAll: (kind: String) -> Unit,
    onOpenLibrary: (JellyfinLibraryInfo) -> Unit,
): (() -> Unit)? = when {
    !section.hasSeeAll -> null
    section.key == JellyfinHomeSectionKeys.FAVOURITES -> onOpenFavorites
    section.key == JellyfinHomeSectionKeys.CONTINUE_WATCHING || section.key == JellyfinHomeSectionKeys.NEXT_UP ->
        section.key.let { key -> { onOpenSeeAll(key) } }
    section.key.startsWith("library:") -> {
        val libraryId = section.key.removePrefix("library:")
        librariesById[libraryId]?.let { library -> { onOpenLibrary(library) } }
    }
    else -> null
}

// The row's own title reads as "Latest in <library>" (see JellyfinHomeViewModel) - fine for a
// content shelf, but the Media row represents the section/library itself, not "what's newest in
// it", so library entries use the library's own name instead. Favourites has no such mismatch.
private fun sectionMediaLabel(section: JellyfinHomeSection, librariesById: Map<String, JellyfinLibraryInfo>): String {
    if (!section.key.startsWith("library:")) return section.title
    val libraryId = section.key.removePrefix("library:")
    return librariesById[libraryId]?.name ?: section.title
}

private data class MediaEntry(val label: String, val onClick: () -> Unit)

@Composable
private fun JellyfinItemRow(
    title: String,
    items: List<JellyfinItemInfo>,
    onOpenItem: (JellyfinItemInfo) -> Unit,
    onSeeAll: (() -> Unit)? = null,
) {
    Column {
        Text(
            text = title,
            color = Palette.TextPrimary,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(items, key = { it.id }) { item ->
                JellyfinPoster(item = item, onClick = { onOpenItem(item) })
            }
            // A poster-sized tile at the end of the row reads as part of the same shelf of
            // content rather than a separate text button off to the side - same affordance as the
            // "See All" text it replaces, just placed where the row itself is being browsed.
            if (onSeeAll != null) {
                item(key = "see_all") {
                    SeeAllTile(onClick = onSeeAll)
                }
            }
        }
    }
}

// Placeholder look (icon + label on a plain surface) rather than real art - "See All" has no
// natural poster image of its own, so this is a deliberate stand-in until this gets a proper
// visual design pass, at which point it'll likely become a styled image instead.
@Composable
private fun SeeAllTile(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .width(120.dp)
            .aspectRatio(2f / 3f)
            .clip(AppShapes.small)
            .background(Palette.Surface)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Palette.Accent)
            Text(text = "See All", color = Palette.Accent, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

// One double-wide card per section (Favourites + each library) as a quick jump-off point,
// separate from the content shelves below it - Continue Watching/Next Up are excluded since
// they're per-item queues, not a "section" in the same sense as a library/Favourites is.
@Composable
private fun JellyfinMediaRow(entries: List<MediaEntry>) {
    Column {
        Text(
            text = "Media",
            color = Palette.TextPrimary,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(entries, key = { it.label }) { entry ->
                MediaCard(label = entry.label, onClick = entry.onClick)
            }
        }
    }
}

// Double-wide (2x a normal poster's width, same height - not a taller 2:3 card) since there's no
// single poster image that represents a whole section - placeholder text card for now, per
// direct feedback that this becomes a styled image once art is picked for each section.
@Composable
private fun MediaCard(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .width(240.dp)
            .height(80.dp)
            .clip(AppShapes.small)
            .background(Palette.Surface)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, color = Palette.TextPrimary)
    }
}

/** [badge] overlays a small pill in the poster's bottom-end corner (e.g. a season's episode count) - null renders nothing extra, the plain poster look every existing caller already expects. */
@Composable
fun JellyfinPoster(item: JellyfinItemInfo, onClick: () -> Unit, badge: String? = null) {
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
            if (badge != null) {
                Text(
                    text = badge,
                    color = Palette.TextPrimary,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .clip(AppShapes.small)
                        .background(Palette.Surface.copy(alpha = 0.85f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
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
