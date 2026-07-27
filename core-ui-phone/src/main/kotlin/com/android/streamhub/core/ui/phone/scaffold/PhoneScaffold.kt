package com.android.streamhub.core.ui.phone.scaffold

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.android.streamhub.core.design.Palette

data class PhoneNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

val phoneNavItems = listOf(
    PhoneNavItem(route = "search", label = "Search", icon = Icons.Filled.Search),
    PhoneNavItem(route = "live_tv", label = "Live TV", icon = Icons.Filled.LiveTv),
    PhoneNavItem(route = "vod", label = "VOD", icon = Icons.Filled.Movie),
    PhoneNavItem(route = "emby_home", label = "Emby", icon = Icons.Filled.VideoLibrary),
    PhoneNavItem(route = "jellyfin_home", label = "Jellyfin", icon = Icons.Filled.PlayCircleFilled),
    PhoneNavItem(route = "settings", label = "Settings", icon = Icons.Filled.Settings),
    // Favorites tab joins this list in a later milestone - both the bar and the rail below already
    // render off this same list, so adding an entry is the only change needed there.
)

// Standard Material3 NavigationBarItem/NavigationRailItem both apply Modifier.weight(1f)
// internally to divide the bar/rail evenly between however many items exist - fine at 5 items,
// but at 7 (since Search joined the list) it started shrinking every item's touch target and
// clipping labels instead. A plain scrollable Row/Column of custom items - each a fixed
// comfortable size rather than an equal share of whatever width is left - fixes both problems:
// it never over-compresses, and it scrolls instead of clipping once content actually overflows.
private const val NAV_ITEM_SIZE_DP = 72

@Composable
private fun ScrollableNavItem(item: PhoneNavItem, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val contentColor = if (selected) Palette.Accent else Palette.TextMuted
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .background(
                    color = if (selected) Palette.Accent.copy(alpha = 0.16f) else Color.Transparent,
                    shape = RoundedCornerShape(50),
                )
                .padding(horizontal = 20.dp, vertical = 4.dp),
        ) {
            Icon(item.icon, contentDescription = item.label, tint = contentColor)
        }
        Text(
            text = item.label,
            color = contentColor,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/**
 * Bottom-nav shell for phone in portrait; becomes a left-side nav rail in landscape instead,
 * where horizontal space is comparatively cheap and vertical space is scarce - Live TV's EPG grid
 * in particular needs every bit of vertical room a phone screen can give it. [bottomBarVisible]
 * lets full-screen destinations (the player) hide the bar/rail without needing a second Scaffold.
 * Both the bar and the rail scroll along their long axis (see [ScrollableNavItem]'s own comment).
 */
@Composable
fun PhoneScaffold(
    currentRoute: String?,
    bottomBarVisible: Boolean,
    onNavigate: (String) -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    if (isLandscape) {
        Row(modifier = Modifier.fillMaxSize()) {
            if (bottomBarVisible) {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(NAV_ITEM_SIZE_DP.dp)
                        .background(Palette.Surface)
                        .statusBarsPadding()
                        .verticalScroll(rememberScrollState()),
                ) {
                    phoneNavItems.forEach { item ->
                        ScrollableNavItem(
                            item = item,
                            selected = currentRoute == item.route,
                            onClick = { onNavigate(item.route) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            // The rail already claims its own width via Row's layout (not an inset content needs
            // to account for the way a stacked bottomBar would), so there's no bar height left for
            // content to reserve space for - screens that need status-bar clearance already handle
            // it themselves via their own TopAppBar/statusBarsPadding, same as in portrait.
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                content(PaddingValues(0.dp))
            }
        }
    } else {
        Scaffold(
            bottomBar = {
                if (bottomBarVisible) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Palette.Surface)
                            .navigationBarsPadding()
                            .horizontalScroll(rememberScrollState()),
                    ) {
                        phoneNavItems.forEach { item ->
                            ScrollableNavItem(
                                item = item,
                                selected = currentRoute == item.route,
                                onClick = { onNavigate(item.route) },
                                modifier = Modifier.width(NAV_ITEM_SIZE_DP.dp),
                            )
                        }
                    }
                }
            },
            content = { padding ->
                content(padding)
            },
        )
    }
}
