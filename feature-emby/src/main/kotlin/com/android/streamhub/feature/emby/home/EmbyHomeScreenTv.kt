package com.android.streamhub.feature.emby.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import com.android.streamhub.core.design.tvFocusBorder
import com.android.streamhub.feature.emby.data.EmbyItemInfo
import com.android.streamhub.feature.emby.data.EmbyItemType
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
    viewModel: EmbyHomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    when {
        !uiState.hasSource -> EmbyAddSourcePromptTv(onSignInClick = onSignInClick, modifier = Modifier.fillMaxSize())
        uiState.isLoading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        uiState.isEmpty -> EmbyEmptyStateTv(modifier = Modifier.fillMaxSize())
        else -> EmbyHomeContentTv(uiState = uiState, onOpenLibrary = onOpenLibrary, onOpenItem = onOpenItem)
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
) {
    // Defaults to the first row's first item so the preview panel is never blank before anything
    // has actually taken D-pad focus - only re-derived when the underlying data itself changes (a
    // fresh load/refresh), not on every unrelated recomposition, so a later focus move here never
    // gets silently reset back to this default. Same pattern as JellyfinHomeScreenTv.
    var previewItem by remember(uiState.continueWatching, uiState.nextUp, uiState.latestSections) {
        val firstItem = uiState.continueWatching.firstOrNull()
            ?: uiState.nextUp.firstOrNull()
            ?: uiState.latestSections.firstOrNull { it.items.isNotEmpty() }?.items?.firstOrNull()
        mutableStateOf(firstItem)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        PreviewPanel(item = previewItem, modifier = Modifier.fillMaxWidth().height(210.dp))

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            uiState.errorMessage?.let { error ->
                item(key = "error") {
                    Text(text = error, color = Palette.Error, modifier = Modifier.fillMaxWidth().padding(24.dp, 4.dp))
                }
            }

            if (uiState.continueWatching.isNotEmpty()) {
                item(key = "continue_watching") {
                    EmbyItemRowTv(
                        title = "Continue Watching",
                        items = uiState.continueWatching,
                        onOpenItem = onOpenItem,
                        onItemFocused = { previewItem = it },
                    )
                }
            }

            if (uiState.nextUp.isNotEmpty()) {
                item(key = "next_up") {
                    EmbyItemRowTv(
                        title = "Next Up",
                        items = uiState.nextUp,
                        onOpenItem = onOpenItem,
                        onItemFocused = { previewItem = it },
                    )
                }
            }

            items(uiState.latestSections, key = { "library:${it.library.id}" }) { section ->
                EmbyItemRowTv(
                    title = "Latest in ${section.library.name}",
                    items = section.items,
                    onOpenItem = onOpenItem,
                    onItemFocused = { previewItem = it },
                    onHeaderClick = { onOpenLibrary(section.library.id, section.library.type.toItemType()) },
                )
            }
        }
    }
}

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
    onItemFocused: (EmbyItemInfo) -> Unit,
    // Non-null only for the "Latest in X" rows - see EmbyItemRow's (phone) matching parameter doc
    // for why the heading itself, not a separate tile, is this row's only library-jump affordance.
    onHeaderClick: (() -> Unit)? = null,
) {
    Column {
        val headerInteractionSource = remember { MutableInteractionSource() }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .let {
                    if (onHeaderClick != null) {
                        it.tvFocusBorder(headerInteractionSource, AppShapes.small)
                            .clickable(interactionSource = headerInteractionSource, indication = null, onClick = onHeaderClick)
                    } else {
                        it
                    }
                }
                .padding(horizontal = 24.dp, vertical = 4.dp),
        ) {
            Text(text = title, color = Palette.TextPrimary)
            if (onHeaderClick != null) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Palette.TextMuted)
            }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(items, key = { it.id }) { item ->
                EmbyPosterTv(
                    item = item,
                    onClick = { onOpenItem(item) },
                    onFocused = { focused -> if (focused) onItemFocused(item) },
                )
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
        scale = CardDefaults.scale(focusedScale = 1.05f),
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
