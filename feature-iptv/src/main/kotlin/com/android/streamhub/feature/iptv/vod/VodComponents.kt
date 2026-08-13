package com.android.streamhub.feature.iptv.vod

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.android.streamhub.core.design.AppShapes
import com.android.streamhub.core.design.Palette
import com.android.streamhub.core.design.tvFocusBorder

/**
 * Shared VOD building blocks - originally private to VodScreenPhone.kt (its only caller when VOD
 * was just one screen), pulled out once VodLibraryScreen.kt (the poster grid) and VodScreenPhone/
 * Tv's new home rows both needed Poster, and Kotlin file-private can't cross files.
 */

// Same focused-grow amount as Jellyfin/Emby's poster Cards (CardDefaults.scale(focusedScale =
// 1.15f)) - VOD's Poster/hero tiles aren't tv-material3 Cards, so this reproduces that same 15%
// grow-on-focus by hand via Modifier.scale() driven off the row's own interactionSource, rather
// than introducing a mismatched, quieter focus feel just for VOD.
internal const val VOD_FOCUSED_SCALE = 1.15f

@Composable
internal fun Poster(name: String, posterUrl: String?, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (isFocused) VOD_FOCUSED_SCALE else 1f, label = "vodPosterFocusScale")
    Column(
        modifier = modifier
            // 10dp (was 4dp) - the 15% grow-on-focus scale below doesn't affect layout size, so
            // this padding is what actually keeps a focused poster from visually overlapping its
            // neighbors; 10dp on each side gives ~20dp of clearance between adjacent posters.
            .padding(10.dp)
            .scale(scale)
            .tvFocusBorder(interactionSource, AppShapes.small)
            .clickable(interactionSource = interactionSource, indication = LocalIndication.current, onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .aspectRatio(2f / 3f)
                .fillMaxWidth()
                .clip(AppShapes.small),
        ) {
            if (posterUrl != null) {
                AsyncImage(
                    model = posterUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(modifier = Modifier.fillMaxSize().background(Palette.Surface))
            }
        }
        Text(
            text = name,
            maxLines = 2,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
internal fun GridDensityButton(gridColumns: Int, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.GridView, contentDescription = "Grid size ($gridColumns columns)")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            // A DropdownMenu never moves D-pad focus into itself on TV - without this, the
            // remote's presses kept hitting whatever was focused underneath, not this menu.
            val firstItemFocusRequester = remember { FocusRequester() }
            LaunchedEffect(Unit) { firstItemFocusRequester.requestFocus() }
            VOD_GRID_COLUMN_OPTIONS.forEachIndexed { index, count ->
                DropdownMenuItem(
                    text = { Text("$count columns") },
                    modifier = if (index == 0) Modifier.focusRequester(firstItemFocusRequester) else Modifier,
                    onClick = { onSelect(count); expanded = false },
                )
            }
        }
    }
}

// Placeholder look (icon + label on a plain surface) - "See All" has no natural poster image of
// its own, same reasoning/shape as JellyfinHomeScreen's own SeeAllTile.
@Composable
internal fun SeeAllTile(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            // Matches Poster's own 10dp - SeeAllTile doesn't scale itself, but it always sits
            // right after a Poster that does, so it needs the same clearance on its leading edge.
            .padding(10.dp)
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
