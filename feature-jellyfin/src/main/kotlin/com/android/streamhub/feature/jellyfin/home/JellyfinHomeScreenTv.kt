package com.android.streamhub.feature.jellyfin.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.android.streamhub.core.design.AppShapes
import com.android.streamhub.core.design.Palette
import com.android.streamhub.core.design.mediaCategoryArtFor
import com.android.streamhub.feature.jellyfin.data.JellyfinHomeSection
import com.android.streamhub.feature.jellyfin.data.JellyfinHomeSectionKeys
import com.android.streamhub.feature.jellyfin.data.JellyfinItemInfo
import com.android.streamhub.feature.jellyfin.data.JellyfinItemType
import com.android.streamhub.feature.jellyfin.data.JellyfinLibraryInfo
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * A dedicated TV layout rather than reusing JellyfinHomeScreen's plain stack of rows - a static
 * preview panel (backdrop/title/synopsis of whatever poster currently has D-pad focus) above
 * scrollable rows is a genuinely TV-only interaction (there's no "focused but not tapped" concept
 * on a touchscreen), matching the reference Jellyfin-for-Android-TV layout rather than a phone
 * screen stretched onto a bigger one.
 */
@Composable
fun JellyfinHomeScreenTv(
    onOpenLibrary: (JellyfinLibraryInfo) -> Unit,
    onOpenItem: (JellyfinItemInfo) -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenSeeAll: (kind: String) -> Unit,
    viewModel: JellyfinHomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    when {
        !uiState.hasSource -> JellyfinAddSourcePromptTv(modifier = Modifier.fillMaxSize())
        uiState.isLoading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        else -> JellyfinHomeContentTv(
            uiState = uiState,
            onOpenLibrary = onOpenLibrary,
            onOpenItem = onOpenItem,
            onOpenFavorites = onOpenFavorites,
            onOpenSeeAll = onOpenSeeAll,
        )
    }
}

@Composable
private fun JellyfinAddSourcePromptTv(modifier: Modifier = Modifier) {
    Box(modifier = modifier.padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("No Jellyfin server signed in", color = Palette.TextPrimary)
            Text(
                text = "Sign in to a Jellyfin server from the Settings tab to browse your library.",
                color = Palette.TextMuted,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun JellyfinHomeContentTv(
    uiState: JellyfinHomeUiState,
    onOpenLibrary: (JellyfinLibraryInfo) -> Unit,
    onOpenItem: (JellyfinItemInfo) -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenSeeAll: (kind: String) -> Unit,
) {
    val librariesById = uiState.libraries.associateBy { it.id }
    // Defaults to the first row's first item so the preview panel is never blank before anything
    // has actually taken D-pad focus - only re-derived when the section data itself changes
    // (a fresh load/refresh), not on every unrelated recomposition, so a later focus move here
    // never gets silently reset back to this default.
    var previewItem by remember(uiState.sections) {
        mutableStateOf(uiState.sections.firstOrNull { it.items.isNotEmpty() }?.items?.firstOrNull())
    }

    Column(modifier = Modifier.fillMaxSize()) {
        PreviewPanel(item = previewItem, modifier = Modifier.fillMaxWidth().height(210.dp))

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 16.dp),
            // 32dp (was 24dp) - a focused poster/card grows 15% on focus (a visual scale, not a
            // layout-size change), so this gutter needs enough slack to keep that growth clear of
            // the row above/below, not just the pre-scale card height.
            verticalArrangement = Arrangement.spacedBy(32.dp),
        ) {
            uiState.errorMessage?.let { error ->
                item(key = "error") {
                    Text(text = error, color = Palette.Error, modifier = Modifier.fillMaxWidth().padding(24.dp, 4.dp))
                }
            }
            item(key = "media") {
                val mediaEntries = uiState.sections.mapNotNull { section ->
                    if (section.key == JellyfinHomeSectionKeys.CONTINUE_WATCHING || section.key == JellyfinHomeSectionKeys.NEXT_UP) return@mapNotNull null
                    val action = sectionSeeAllAction(section, librariesById, onOpenFavorites, onOpenSeeAll, onOpenLibrary) ?: return@mapNotNull null
                    MediaEntryTv(label = sectionMediaLabel(section, librariesById), onClick = action)
                }
                if (mediaEntries.isNotEmpty()) {
                    JellyfinMediaRowTv(entries = mediaEntries)
                }
            }
            items(uiState.sections, key = { it.key }) { section ->
                // Same key-to-callback mapping as JellyfinHomeScreen's own JellyfinHomeContent -
                // see that composable's comment for why this stays here rather than in the ViewModel.
                val onSeeAll = sectionSeeAllAction(section, librariesById, onOpenFavorites, onOpenSeeAll, onOpenLibrary)
                JellyfinItemRowTv(
                    title = section.title,
                    items = section.items,
                    onOpenItem = onOpenItem,
                    onSeeAll = onSeeAll,
                    onItemFocused = { previewItem = it },
                )
            }
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

private data class MediaEntryTv(val label: String, val onClick: () -> Unit)

@Composable
private fun PreviewPanel(item: JellyfinItemInfo?, modifier: Modifier = Modifier) {
    // Falls back to the poster/thumbnail when there's no dedicated backdrop - Continue Watching/
    // Next Up are mostly individual episodes, which often have no backdrop of their own even
    // though the series does, so without this the panel would just be a blank black box for most
    // of what's actually in those rows.
    val heroImageUrl = item?.backdropImageUrl ?: item?.primaryImageUrl

    Box(modifier = modifier.background(Color.Black)) {
        if (heroImageUrl != null) {
            AsyncImage(
                model = heroImageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        // Scrim fades from opaque (where the text sits) to transparent, rather than a flat
        // overlay across the whole backdrop - keeps the image itself visible on the side the text
        // doesn't cover, matching the reference layout.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.9f), Color.Black.copy(alpha = 0.5f), Color.Transparent),
                    ),
                ),
        )
        if (item != null) {
            // For an episode, the series is the recognizable "brand" (its logo/name is what a
            // viewer actually recognizes at a glance) - the episode's own title becomes a second,
            // "S2:E5 - Title" line instead, the same way official Jellyfin clients present it.
            val isEpisode = item.type == JellyfinItemType.EPISODE
            val heroTitle = if (isEpisode) item.seriesName ?: item.name else item.name
            val episodeLabel = if (isEpisode) {
                val seasonEpisode = if (item.parentIndexNumber != null && item.indexNumber != null) {
                    "S${item.parentIndexNumber}:E${item.indexNumber}"
                } else {
                    null
                }
                listOfNotNull(seasonEpisode, item.name.takeIf { it.isNotBlank() }).joinToString(" - ").takeIf { it.isNotBlank() }
            } else {
                null
            }

            Column(modifier = Modifier.align(Alignment.BottomStart).padding(20.dp).fillMaxWidth(0.6f)) {
                // Themed wordmark logo when the server has one (movies/series; episodes don't
                // carry their own) instead of a plain text title - falls back to text either way,
                // same reasoning as the hero image fallback above.
                if (item.logoImageUrl != null) {
                    AsyncImage(
                        model = item.logoImageUrl,
                        contentDescription = heroTitle,
                        contentScale = ContentScale.Fit,
                        alignment = Alignment.CenterStart,
                        modifier = Modifier.fillMaxWidth().height(64.dp),
                    )
                } else {
                    // Plain white rather than a Palette/theme-derived color - this text always
                    // sits on the dark scrim above, regardless of the app's own light/dark
                    // setting, same as every other "text over a video/image backdrop" spot in
                    // this app (PlayerScreenTv, LiveFullscreenOverlay, EpgGridPanel).
                    Text(text = heroTitle, style = MaterialTheme.typography.titleLarge, color = Color.White)
                }

                episodeLabel?.let { label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }

                val subtitleParts = buildList {
                    item.productionYear?.let { add(it.toString()) }
                    item.runtimeMinutes?.let { add(formatRuntimeMinutes(it)) }
                    item.communityRating?.let { add("$it★") }
                    item.runtimeMinutes?.let { minutes ->
                        val endsAt = LocalTime.now().plusMinutes(minutes.toLong()).format(DateTimeFormatter.ofPattern("HH:mm"))
                        add("Ends at $endsAt")
                    }
                }
                if (subtitleParts.isNotEmpty()) {
                    Text(
                        text = subtitleParts.joinToString(" • "),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.75f),
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }

                item.overview?.let { overview ->
                    Text(
                        text = overview,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = Color.White.copy(alpha = 0.75f),
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
    }
}

private fun formatRuntimeMinutes(minutes: Int): String {
    val hours = minutes / 60
    val remainingMinutes = minutes % 60
    return if (hours > 0) "${hours}h ${remainingMinutes}m" else "${remainingMinutes}m"
}

@Composable
private fun JellyfinItemRowTv(
    title: String,
    items: List<JellyfinItemInfo>,
    onOpenItem: (JellyfinItemInfo) -> Unit,
    onSeeAll: (() -> Unit)?,
    onItemFocused: (JellyfinItemInfo) -> Unit,
) {
    Column {
        Text(
            text = title,
            color = Palette.TextPrimary,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            items(items, key = { it.id }) { item ->
                JellyfinPosterTv(
                    item = item,
                    onClick = { onOpenItem(item) },
                    onFocused = { focused -> if (focused) onItemFocused(item) },
                )
            }
            // A poster-sized tile at the end of the row reads as part of the same shelf of
            // content rather than a separate text button off to the side - same affordance as the
            // "See All" text it replaces, just placed where the row itself is being browsed.
            if (onSeeAll != null) {
                item(key = "see_all") {
                    SeeAllTileTv(onClick = onSeeAll)
                }
            }
        }
    }
}

// One double-wide card per section (Favourites + each library) as a quick jump-off point,
// separate from the content shelves below it - Continue Watching/Next Up are excluded since
// they're per-item queues, not a "section" in the same sense as a library/Favourites is.
@Composable
private fun JellyfinMediaRowTv(entries: List<MediaEntryTv>) {
    Column {
        Text(
            text = "Media",
            color = Palette.TextPrimary,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            items(entries, key = { it.label }) { entry ->
                MediaCardTv(label = entry.label, onClick = entry.onClick)
            }
        }
    }
}

// Double-wide (2x a normal poster's width, same height - not a taller 2:3 card) since there's no
// single poster image that represents a whole section - placeholder text card for now, per
// direct feedback that this becomes a styled image once art is picked for each section.
@Composable
private fun MediaCardTv(label: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        scale = CardDefaults.scale(focusedScale = 1.15f),
        modifier = Modifier.width(188.dp),
    ) {
        Box(
            modifier = Modifier
                .height(141.dp)
                .fillMaxWidth()
                .clip(AppShapes.small)
                .background(Palette.Surface),
            contentAlignment = Alignment.Center,
        ) {
            // Hardcoded hero art for this specific server's library names (see
            // mediaCategoryArtFor's own doc) - the label is already baked into the art itself, so
            // no text overlay when one matches. Falls back to the plain placeholder look for
            // anything that doesn't.
            val art = mediaCategoryArtFor(label)
            if (art != null) {
                AsyncImage(model = art, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Text(text = label, color = Palette.TextPrimary)
            }
        }
    }
}

// Placeholder look (icon + label on a plain surface) rather than real art - "See All" has no
// natural poster image of its own, so this is a deliberate stand-in until this gets a proper
// visual design pass, at which point it'll likely become a styled image instead. Same
// width/scale as JellyfinPosterTv so it sits in the row like any other poster.
@Composable
private fun SeeAllTileTv(onClick: () -> Unit) {
    Card(
        onClick = onClick,
        scale = CardDefaults.scale(focusedScale = 1.15f),
        modifier = Modifier.width(94.dp),
    ) {
        Box(
            modifier = Modifier
                .aspectRatio(2f / 3f)
                .fillMaxWidth()
                .clip(AppShapes.small)
                .background(Palette.Surface),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Palette.Accent)
                Text(text = "See All", color = Palette.Accent, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

// No title beneath the poster - the preview panel above already shows the focused item's name
// (and everything else about it), so a repeated label here would just be clutter. Width is 2/3 of
// the original 140dp per feedback that the rows read too large. focusedScale was originally half
// of CardDefaults' own default growth (1.1f) since the full-size grow-on-focus was tall enough to
// visually cover the "Next Up" row/title sitting just above it - bumped back up to 1.15f (above
// even that original default) per direct feedback that the shrunk version read as barely
// noticeable; watch for that same title-overlap issue resurfacing if this needs tuning further.
/**
 * [badge] overlays a small pill in the poster's bottom-end corner (e.g. a season's total episode
 * count) - null renders nothing extra, the plain poster look every existing caller already
 * expects. [unwatchedCount] overlays a small roundel top-end (a separate corner from [badge], so
 * the two never collide) - the unwatched count when > 0, or a checkmark once fully watched (0);
 * null renders nothing.
 */
@Composable
fun JellyfinPosterTv(
    item: JellyfinItemInfo,
    onClick: () -> Unit,
    onFocused: (Boolean) -> Unit = {},
    badge: String? = null,
    unwatchedCount: Int? = null,
) {
    Card(
        onClick = onClick,
        scale = CardDefaults.scale(focusedScale = 1.15f),
        modifier = Modifier.width(94.dp).onFocusChanged { state -> onFocused(state.isFocused) },
    ) {
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
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .clip(AppShapes.small)
                        .background(Palette.Surface.copy(alpha = 0.85f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            if (unwatchedCount != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(if (unwatchedCount == 0) Palette.Accent else Palette.Surface.copy(alpha = 0.9f)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (unwatchedCount == 0) {
                        Icon(Icons.Filled.Check, contentDescription = "All watched", tint = Palette.TextPrimary, modifier = Modifier.size(12.dp))
                    } else {
                        Text(text = unwatchedCount.toString(), color = Palette.TextPrimary, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}
