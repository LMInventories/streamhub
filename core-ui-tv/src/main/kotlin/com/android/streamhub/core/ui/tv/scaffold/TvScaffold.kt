package com.android.streamhub.core.ui.tv.scaffold

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.android.streamhub.core.design.Palette
import com.android.streamhub.core.design.tvFocusBorder

data class TvNavItem(val route: String, val label: String, val icon: ImageVector)

val tvNavItems = listOf(
    TvNavItem(route = "home", label = "Home", icon = Icons.Filled.Home),
    TvNavItem(route = "search", label = "Search", icon = Icons.Filled.Search),
    TvNavItem(route = "live_tv", label = "Live TV", icon = Icons.Filled.LiveTv),
    TvNavItem(route = "vod", label = "VOD", icon = Icons.Filled.Movie),
    TvNavItem(route = "emby_home", label = "Emby", icon = Icons.Filled.VideoLibrary),
    TvNavItem(route = "jellyfin_home", label = "Jellyfin", icon = Icons.Filled.PlayCircleFilled),
    TvNavItem(route = "settings", label = "Settings", icon = Icons.Filled.Settings),
    // Same list-driven shape (and now the same icon set) as PhoneScaffold's landscape rail -
    // Favourites joins both lists together in a later milestone.
)

private const val NAV_ITEM_WIDTH_DP = 96

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
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(NAV_ITEM_WIDTH_DP.dp)
                    .background(Palette.Surface)
                    .verticalScroll(rememberScrollState()),
            ) {
                tvNavItems.forEach { item ->
                    TvNavRailItem(
                        item = item,
                        selected = currentRoute == item.route,
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
private fun TvNavRailItem(item: TvNavItem, selected: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val contentColor = if (selected) Palette.Accent else Palette.TextMuted
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .tvFocusBorder(interactionSource, RoundedCornerShape(12.dp))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .background(
                    color = if (selected) Palette.Accent.copy(alpha = 0.16f) else Color.Transparent,
                    shape = RoundedCornerShape(50),
                )
                .padding(horizontal = 18.dp, vertical = 8.dp),
        ) {
            Icon(item.icon, contentDescription = item.label, tint = contentColor)
        }
        Text(
            text = item.label,
            color = contentColor,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
