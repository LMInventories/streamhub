package com.android.streamhub.core.ui.tv.scaffold

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusGroup
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.android.streamhub.core.design.AppShapes
import com.android.streamhub.core.design.Palette
import com.android.streamhub.core.design.tvSettingsFocusIndicator

data class TvNavItem(val route: String, val label: String, val icon: ImageVector)

val tvNavItems = listOf(
    TvNavItem(route = "search", label = "Search", icon = Icons.Filled.Search),
    TvNavItem(route = "live_tv", label = "Live TV", icon = Icons.Filled.LiveTv),
    TvNavItem(route = "vod", label = "VOD", icon = Icons.Filled.Movie),
    TvNavItem(route = "emby_home", label = "Emby", icon = Icons.Filled.VideoLibrary),
    TvNavItem(route = "jellyfin_home", label = "Jellyfin", icon = Icons.Filled.PlayCircleFilled),
    TvNavItem(route = "settings", label = "Settings", icon = Icons.Filled.Settings),
    // Same list-driven shape (and now the same icon set) as PhoneScaffold's landscape rail -
    // Favourites joins both lists together in a later milestone.
)

// Icon-only at rest; expands to reveal labels while D-pad focus is anywhere inside the rail - the
// YouTube TV/Netflix rail pattern, a meaningfully better fit for a 6-item TV nav than a
// permanently-fixed icon strip once "look amazing" was the explicit ask.
private const val NAV_RAIL_COLLAPSED_WIDTH_DP = 72
private const val NAV_RAIL_EXPANDED_WIDTH_DP = 220

/**
 * Left icon rail rather than a top tab row - per direct feedback, TV should look like phone held
 * in landscape (PhoneScaffold's own NavigationRail) rather than a separately-styled top nav.
 * Custom item (not tv-material3's Tab/TabRow) for the same reason PhoneScaffold's landscape rail
 * uses a custom item instead of Material3's NavigationRailItem: full control over a fixed,
 * comfortable size and an explicit D-pad focus border, rather than a component built around a
 * different interaction model (Tab/TabRow's onFocus-driven selection turned out to fight the
 * D-pad's own focus movement - see the prior git history for that whole story).
 */
@Composable
fun TvScaffold(
    currentRoute: String?,
    tabRowVisible: Boolean,
    onNavigate: (String) -> Unit,
    content: @Composable () -> Unit,
) {
    Row(modifier = Modifier.fillMaxSize()) {
        if (tabRowVisible) {
            var railFocused by remember { mutableStateOf(false) }
            val railWidth by animateDpAsState(
                targetValue = if (railFocused) NAV_RAIL_EXPANDED_WIDTH_DP.dp else NAV_RAIL_COLLAPSED_WIDTH_DP.dp,
                label = "tvNavRailWidth",
            )
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(railWidth)
                    .background(Palette.Surface)
                    // focusGroup (not focusable itself) lets onFocusChanged report "focus is
                    // somewhere inside this subtree" without the rail Column becoming its own
                    // separate D-pad stop - the same combination Compose's own docs use for
                    // exactly this "expand a rail while a descendant is focused" pattern.
                    .focusGroup()
                    .onFocusChanged { railFocused = it.hasFocus }
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
            ) {
                tvNavItems.forEach { item ->
                    TvNavRailItem(
                        item = item,
                        selected = currentRoute == item.route,
                        expanded = railFocused,
                        onClick = { onNavigate(item.route) },
                    )
                }
            }
        }
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            content()
        }
    }
}

@Composable
private fun TvNavRailItem(item: TvNavItem, selected: Boolean, expanded: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val contentColor = if (selected) Palette.Accent else Palette.TextMuted
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 4.dp)
            .tvSettingsFocusIndicator(interactionSource, selected = selected, shape = AppShapes.small)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp)
            .animateContentSize(),
    ) {
        Icon(item.icon, contentDescription = if (expanded) null else item.label, tint = contentColor)
        if (expanded) {
            Text(
                text = item.label,
                color = contentColor,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 16.dp),
            )
        }
    }
}
