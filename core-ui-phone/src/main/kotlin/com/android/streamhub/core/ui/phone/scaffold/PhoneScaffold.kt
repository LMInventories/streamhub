package com.android.streamhub.core.ui.phone.scaffold

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp

data class PhoneNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

val phoneNavItems = listOf(
    PhoneNavItem(route = "home", label = "Home", icon = Icons.Filled.Home),
    PhoneNavItem(route = "search", label = "Search", icon = Icons.Filled.Search),
    PhoneNavItem(route = "live_tv", label = "Live TV", icon = Icons.Filled.LiveTv),
    PhoneNavItem(route = "vod", label = "VOD", icon = Icons.Filled.Movie),
    PhoneNavItem(route = "emby_home", label = "Emby", icon = Icons.Filled.VideoLibrary),
    PhoneNavItem(route = "jellyfin_home", label = "Jellyfin", icon = Icons.Filled.PlayCircleFilled),
    PhoneNavItem(route = "settings", label = "Settings", icon = Icons.Filled.Settings),
    // Favorites tab joins this list in a later milestone - both the bar and the rail below already
    // render off this same list, so adding an entry is the only change needed there.
)

/**
 * Bottom-nav shell for phone in portrait; becomes a left-side nav rail in landscape instead,
 * where horizontal space is comparatively cheap and vertical space is scarce - Live TV's EPG grid
 * in particular needs every bit of vertical room a phone screen can give it. [bottomBarVisible]
 * lets full-screen destinations (the player) hide the bar/rail without needing a second Scaffold.
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
                NavigationRail {
                    phoneNavItems.forEach { item ->
                        NavigationRailItem(
                            selected = currentRoute == item.route,
                            onClick = { onNavigate(item.route) },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) },
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
                    NavigationBar {
                        phoneNavItems.forEach { item ->
                            NavigationBarItem(
                                selected = currentRoute == item.route,
                                onClick = { onNavigate(item.route) },
                                icon = { Icon(item.icon, contentDescription = item.label) },
                                label = { Text(item.label) },
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
