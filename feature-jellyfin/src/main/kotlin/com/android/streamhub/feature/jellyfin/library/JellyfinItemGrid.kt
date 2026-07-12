package com.android.streamhub.feature.jellyfin.library

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import coil3.compose.AsyncImage
import com.android.streamhub.core.design.AppShapes
import com.android.streamhub.core.design.Palette
import com.android.streamhub.core.design.tvFocusBorder
import com.android.streamhub.feature.jellyfin.data.JellyfinItemInfo

/**
 * Paginated poster grid shared by JellyfinLibraryScreen and JellyfinFavoritesScreen - both are
 * "here's a list of items, load more as the user scrolls near the end" with nothing else
 * distinguishing them visually.
 */
@Composable
fun JellyfinItemGrid(
    items: List<JellyfinItemInfo>,
    isLoading: Boolean,
    onLoadMore: () -> Unit,
    onOpenItem: (JellyfinItemInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    val gridState = rememberLazyGridState()

    // Survives a push-then-pop back to this screen (Navigation-Compose keeps a backstack entry's
    // SaveableStateHolder alive as long as the entry itself is still on the backstack), the same
    // mechanism that already lets gridState's scroll position survive that trip. Restoring D-pad
    // focus onto this exact item, rather than wherever the grid's default focus target is, is what
    // makes "back out of a poster, come straight back to the same poster" possible.
    var focusedItemId by rememberSaveable { mutableStateOf<String?>(null) }

    // Plain (non-saveable) - deliberately resets to false on every fresh entry into this screen so
    // the restore-once-per-visit below fires again each time the user returns, while still being
    // idempotent within one continuous composition (so later focus changes don't re-trigger it).
    var hasRestoredFocus by remember { mutableStateOf(false) }

    // Loads the next page once the user has scrolled within one row of the end, rather than
    // waiting until they hit a hard "load more" button - matches the paginated-grid pattern
    // Findroid's own LibraryScreen uses for the same reason (Jellyfin libraries can be large).
    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= items.size - 12
        }
    }
    LaunchedEffect(shouldLoadMore, items.size) {
        if (shouldLoadMore) onLoadMore()
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(minSize = 120.dp),
            contentPadding = PaddingValues(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(items, key = { it.id }) { item ->
                JellyfinPosterCard(
                    item = item,
                    onClick = { onOpenItem(item) },
                    onFocused = { focusedItemId = item.id },
                    restoreFocus = !hasRestoredFocus && item.id == focusedItemId,
                    onFocusRestored = { hasRestoredFocus = true },
                )
            }
        }

        if (isLoading && items.isEmpty()) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
    }
}

@Composable
private fun JellyfinPosterCard(
    item: JellyfinItemInfo,
    onClick: () -> Unit,
    onFocused: () -> Unit,
    restoreFocus: Boolean,
    onFocusRestored: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(restoreFocus) {
        if (restoreFocus) {
            onFocusRestored()
            runCatching { focusRequester.requestFocus() }
        }
    }
    Column(
        modifier = Modifier
            .padding(6.dp)
            .focusRequester(focusRequester)
            .onFocusChanged { if (it.isFocused) onFocused() }
            .tvFocusBorder(interactionSource, AppShapes.small)
            .clickable(interactionSource = interactionSource, indication = LocalIndication.current, onClick = onClick),
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
        Text(
            text = item.name,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = Palette.TextPrimary,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
