package com.android.streamhub.feature.emby.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import com.android.streamhub.core.design.tvFocusBorder
import com.android.streamhub.feature.emby.data.EmbyHomeSection
import com.android.streamhub.feature.emby.data.EmbyHomeSectionKeys
import com.android.streamhub.feature.emby.data.EmbyItemInfo
import com.android.streamhub.feature.emby.data.EmbyItemType
import com.android.streamhub.feature.emby.data.EmbyLibraryInfo
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * A dedicated TV layout rather than reusing EmbyHomeScreen's plain stack of rows - a static
 * preview panel (backdrop/title/synopsis of whatever poster currently has D-pad focus) above
 * scrollable rows is a genuinely TV-only interaction (there's no "focused but not tapped" concept
 * on a touchscreen), matching JellyfinHomeScreenTv's own layout.
 */
@Composable
fun EmbyHomeScreenTv(
    onOpenLibrary: (libraryId: String, itemType: EmbyItemType) -> Unit,
    onOpenItem: (EmbyItemInfo) -> Unit,
    onSignInClick: () -> Unit,
    onOpenFavorites: () -> Unit,
    viewModel: EmbyHomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    when {
        !uiState.hasSource -> EmbyAddSourcePromptTv(onSignInClick = onSignInClick, modifier = Modifier.fillMaxSize())
        uiState.isLoading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        uiState.isEmpty -> EmbyEmptyStateTv(modifier = Modifier.fillMaxSize())
        else -> EmbyHomeContentTv(
            uiState = uiState,
            onOpenLibrary = onOpenLibrary,
            onOpenItem = onOpenItem,
            onOpenFavorites = onOpenFavorites,
        )
    }
}

@Composable
private fun EmbyAddSourcePromptTv(onSignInClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier.padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("No Emby server signed in", color = Palette.TextPrimary)
            Text(
                text = "Sign in to an Emby server to browse your library.",
                color = Palette.TextMuted,
                modifier = Modifier.padding(top = 8.dp),
            )
            val interactionSource = remember { MutableInteractionSource() }
            Text(
                text = "Sign In",
                color = Palette.Accent,
                modifier = Modifier
                    .padding(top = 16.dp)
                    .clip(AppShapes.small)
                    .tvFocusBorder(interactionSource, AppShapes.small)
                    .clickable(interactionSource = interactionSource, indication = null, onClick = onSignInClick)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun EmbyEmptyStateTv(modifier: Modifier = Modifier) {
    Box(modifier = modifier.padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text = "No media found on this server yet.", color = Palette.TextMuted)
    }
}

@Composable
private fun EmbyHomeContentTv(
    uiState: EmbyHomeUiState,
    onOpenLibrary: (libraryId: String, itemType: EmbyItemType) -> Unit,
    onOpenItem: (EmbyItemInfo) -> Unit,
    onOpenFavorites: () -> Unit,
) {
    val librariesById = uiState.libraries.associateBy { it.id }
    // Defaults to the first row's first item so the preview panel is never blank before anything
    // has actually taken D-pad focus - only re-derived when the section data itself changes (a
    // fresh load/refresh), not on every unrelated recomposition, so a later focus move here never
    // gets silently reset back to this default. Priority mirrors the old typed-field fallback
    // chain (Continue Watching, then Next Up, then Favourites, then the first section with any
    // items at all) now sourced from the unified sections list instead.
    var previewItem by remember(uiState.sections) {
        val firstItem = uiState.sections.firstOrNull { it.key == EmbyHomeSectionKeys.CONTINUE_WATCHING }?.items?.firstOrNull()
            ?: uiState.sections.firstOrNull { it.key == EmbyHomeSectionKeys.NEXT_UP }?.items?.firstOrNull()
            ?: uiState.sections.firstOrNull { it.key == EmbyHomeSectionKeys.FAVOURITES }?.items?.firstOrNull()
            ?: uiState.sections.firstOrNull { it.items.isNotEmpty() }?.items?.firstOrNull()
        mutableStateOf(firstItem)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        PreviewPanel(item = previewItem, modifier = Modifier.fillMaxWidth().height(210.dp))

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 16.dp),
            // 32dp (was 24dp) - see JellyfinHomeScreenTv's matching comment: a focused card's 15%
            // scale doesn't affect layout size, so this gutter needs slack for that growth.
            verticalArrangement = Arrangement.spacedBy(32.dp),
        ) {
            uiState.errorMessage?.let { error ->
                item(key = "error") {
                    Text(text = error, color = Palette.Error, modifier = Modifier.fillMaxWidth().padding(24.dp, 4.dp))
                }
            }
            item(key = "media") {
                val mediaEntries = uiState.sections.mapNotNull { section ->
                    if (section.key == EmbyHomeSectionKeys.CONTINUE_WATCHING || section.key == EmbyHomeSectionKeys.NEXT_UP) return@mapNotNull null
                    val action = sectionSeeAllAction(section, librariesById, onOpenFavorites, onOpenLibrary) ?: return@mapNotNull null
                    MediaEntryTv(label = sectionMediaLabel(section, librariesById), onClick = action)
                }
                if (mediaEntries.isNotEmpty()) {
                    EmbyMediaRowTv(entries = mediaEntries)
                }
            }
            items(uiState.sections, key = { it.key }) { section ->
                // Same key-to-callback mapping as EmbyHomeScreen's own EmbyHomeContent - see that
                // composable's comment for why this stays here rather than in the ViewModel.
                val onSeeAll = sectionSeeAllAction(section, librariesById, onOpenFavorites, onOpenLibrary)
                EmbyItemRowTv(
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

private data class MediaEntryTv(val label: String, val onClick: () -> Unit)

@Composable
private fun PreviewPanel(item: EmbyItemInfo?, modifier: Modifier = Modifier) {
    // Falls back to the poster/thumbnail when there's no dedicated backdrop - Continue Watching/
    // Next Up are mostly individual episodes, which often have no backdrop of their own even
    // though the series does, so without this the panel would just be a blank black box for most
    // of what's actually in those rows. Same reasoning as JellyfinHomeScreenTv's PreviewPanel.
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
        // Scrim fades from opaque (where the text sits) to transparent, rather than a flat overlay
        // across the whole backdrop - keeps the image visible on the side the text doesn't cover.
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
            // For an episode, the series is the recognizable "brand" - the episode's own title
            // becomes a second, "S2:E5 - Title" line instead, same as Jellyfin's PreviewPanel.
            // EmbyItemInfo carries no logoImageUrl (trimmed from the data model this pass), so
            // unlike Jellyfin's this always renders plain text, never a wordmark image.
            val isEpisode = item.type == EmbyItemType.EPISODE
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
                // Plain white rather than a Palette/theme-derived color - this text always sits on
                // the dark scrim above, regardless of the app's own light/dark setting, same as
                // every other "text over a video/image backdrop" spot in this app.
                Text(text = heroTitle, style = MaterialTheme.typography.titleLarge, color = Color.White)

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
private fun EmbyItemRowTv(
    title: String,
    items: List<EmbyItemInfo>,
    onOpenItem: (EmbyItemInfo) -> Unit,
    onSeeAll: (() -> Unit)?,
    onItemFocused: (EmbyItemInfo) -> Unit,
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
                EmbyPosterTv(
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
private fun EmbyMediaRowTv(entries: List<MediaEntryTv>) {
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
// single poster image that represents a whole section - placeholder text card for now, per direct
// feedback (on the Jellyfin equivalent this was ported from) that this becomes a styled image once
// art is picked for each section.
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
            // anything that doesn't. Same art set as JellyfinHomeScreenTv's matching MediaCardTv,
            // since both servers use the same library names.
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
// width/scale as EmbyPosterTv so it sits in the row like any other poster.
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

// No title beneath the poster - the preview panel above already shows the focused item's name (and
// everything else about it), so a repeated label here would just be clutter. Same 94dp width/0.05f
// focusedScale as JellyfinPosterTv.
@Composable
fun EmbyPosterTv(item: EmbyItemInfo, onClick: () -> Unit, onFocused: (Boolean) -> Unit = {}) {
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
        }
    }
}
