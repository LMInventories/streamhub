package com.android.streamhub.feature.jellyfin.home

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
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.android.streamhub.core.design.AppShapes
import com.android.streamhub.core.design.Palette
import com.android.streamhub.core.design.tvFocusBorder
import com.android.streamhub.feature.jellyfin.data.JellyfinHomeSectionKeys
import com.android.streamhub.feature.jellyfin.data.JellyfinItemInfo
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
        )
    }
}

@Composable
private fun JellyfinAddSourcePromptTv(modifier: Modifier = Modifier) {
    Box(modifier = modifier.padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("No Jellyfin server signed in")
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
        PreviewPanel(item = previewItem, modifier = Modifier.fillMaxWidth().height(420.dp))

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
            items(uiState.sections, key = { it.key }) { section ->
                // Same key-to-callback mapping as JellyfinHomeScreen's own JellyfinHomeContent -
                // see that composable's comment for why this stays here rather than in the ViewModel.
                val onSeeAll: (() -> Unit)? = when {
                    !section.hasSeeAll -> null
                    section.key == JellyfinHomeSectionKeys.FAVOURITES -> onOpenFavorites
                    section.key.startsWith("library:") -> {
                        val libraryId = section.key.removePrefix("library:")
                        librariesById[libraryId]?.let { library -> { onOpenLibrary(library) } }
                    }
                    else -> null
                }
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

@Composable
private fun PreviewPanel(item: JellyfinItemInfo?, modifier: Modifier = Modifier) {
    Box(modifier = modifier.background(Color.Black)) {
        if (item?.backdropImageUrl != null) {
            AsyncImage(
                model = item.backdropImageUrl,
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
                        colors = listOf(Color.Black.copy(alpha = 0.9f), Color.Black.copy(alpha = 0.4f), Color.Transparent),
                    ),
                ),
        )
        if (item != null) {
            Column(modifier = Modifier.align(Alignment.BottomStart).padding(32.dp).fillMaxWidth(0.6f)) {
                Text(text = item.name, style = MaterialTheme.typography.headlineMedium)

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
                    Text(text = subtitleParts.joinToString(" • "), color = Palette.TextMuted, modifier = Modifier.padding(top = 6.dp))
                }

                item.overview?.let { overview ->
                    Text(
                        text = overview,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        color = Palette.TextMuted,
                        modifier = Modifier.padding(top = 10.dp),
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
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = title)
            if (onSeeAll != null) {
                val interactionSource = remember { MutableInteractionSource() }
                Text(
                    text = "See All",
                    color = Palette.Accent,
                    modifier = Modifier
                        .tvFocusBorder(interactionSource, AppShapes.small)
                        .clickable(interactionSource = interactionSource, indication = null, onClick = onSeeAll)
                        .padding(4.dp),
                )
            }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(items, key = { it.id }) { item ->
                JellyfinPosterTv(
                    item = item,
                    onClick = { onOpenItem(item) },
                    onFocused = { focused -> if (focused) onItemFocused(item) },
                )
            }
        }
    }
}

@Composable
private fun JellyfinPosterTv(item: JellyfinItemInfo, onClick: () -> Unit, onFocused: (Boolean) -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.width(140.dp).onFocusChanged { state -> onFocused(state.isFocused) },
    ) {
        Column {
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
                text = item.seriesName ?: item.name,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(8.dp),
            )
        }
    }
}
