package com.android.streamhub.feature.emby.home

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
import com.android.streamhub.core.design.mediaCategoryArtFor
import com.android.streamhub.core.ui.phone.theme.appColorScheme
import com.android.streamhub.feature.emby.data.EmbyHomeSection
import com.android.streamhub.feature.emby.data.EmbyHomeSectionKeys
import com.android.streamhub.feature.emby.data.EmbyItemInfo
import com.android.streamhub.feature.emby.data.EmbyItemType
import com.android.streamhub.feature.emby.data.EmbyLibraryInfo

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
                if (section.key == EmbyHomeSectionKeys.CONTINUE_WATCHING || section.key == EmbyHomeSectionKeys.NEXT_UP) return@mapNotNull null
                val action = sectionSeeAllAction(section, librariesById, onOpenFavorites, onOpenLibrary) ?: return@mapNotNull null
                MediaEntry(label = sectionMediaLabel(section, librariesById), onClick = action)
            }
            if (mediaEntries.isNotEmpty()) {
                EmbyMediaRow(entries = mediaEntries)
            }
        }

        items(uiState.sections, key = { it.key }) { section ->
            // Each section only knows its own key/hasSeeAll (set once in the ViewModel, alongside
            // every other section's, regardless of user-chosen order) - mapping a key back to the
            // actual navigation callback stays here, since the ViewModel layer shouldn't carry
            // UI-bound lambdas.
            val onSeeAll = sectionSeeAllAction(section, librariesById, onOpenFavorites, onOpenLibrary)
            EmbyItemRow(title = section.title, items = section.items, onOpenItem = onOpenItem, onSeeAll = onSeeAll)
        }
    }
}

private fun sectionSeeAllAction(
    section: EmbyHomeSection,
    librariesById: Map<String, EmbyLibraryInfo>,
    onOpenFavorites: () -> Unit,
    onOpenLibrary: (libraryId: String, itemType: EmbyItemType) -> Unit,
): (() -> Unit)? = when {
    !section.hasSeeAll -> null
    section.key == EmbyHomeSectionKeys.FAVOURITES -> onOpenFavorites
    section.key.startsWith("library:") -> {
        val libraryId = section.key.removePrefix("library:")
        librariesById[libraryId]?.let { library -> { onOpenLibrary(library.id, library.type.toItemType()) } }
    }
    else -> null
}

// The row's own title reads as "Latest in <library>" (see EmbyHomeViewModel) - fine for a content
// shelf, but the Media row represents the section/library itself, not "what's newest in it", so
// library entries use the library's own name instead. Favourites has no such mismatch.
private fun sectionMediaLabel(section: EmbyHomeSection, librariesById: Map<String, EmbyLibraryInfo>): String {
    if (!section.key.startsWith("library:")) return section.title
    val libraryId = section.key.removePrefix("library:")
    return librariesById[libraryId]?.name ?: section.title
}

private data class MediaEntry(val label: String, val onClick: () -> Unit)

@Composable
private fun EmbyItemRow(
    title: String,
    items: List<EmbyItemInfo>,
    onOpenItem: (EmbyItemInfo) -> Unit,
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
                EmbyPoster(item = item, onClick = { onOpenItem(item) })
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
private fun EmbyMediaRow(entries: List<MediaEntry>) {
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
// single poster image that represents a whole section - placeholder text card for now, per direct
// feedback (on the Jellyfin equivalent this was ported from) that this becomes a styled image once
// art is picked for each section.
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
        // Hardcoded hero art for this specific server's library names (see mediaCategoryArtFor's
        // own doc) - the label is already baked into the art itself, so no text overlay when one
        // matches. Falls back to the plain placeholder look for anything that doesn't. Same art set
        // as JellyfinHomeScreen's matching MediaCard, since both servers use the same library names.
        val art = mediaCategoryArtFor(label)
        if (art != null) {
            AsyncImage(model = art, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Text(text = label, color = Palette.TextPrimary)
        }
    }
}

/** Plain poster + caption - no badge/unwatched-count overlays (EmbyItemInfo carries no unplayed-count field this pass, see the data model's own doc comment). */
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
