package com.android.streamhub.core.ui.phone.scaffold

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector

data class PhoneNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

val phoneNavItems = listOf(
    PhoneNavItem(route = "home", label = "Home", icon = Icons.Filled.Home),
    PhoneNavItem(route = "live_tv", label = "Live TV", icon = Icons.Filled.LiveTv),
    PhoneNavItem(route = "vod", label = "VOD", icon = Icons.Filled.Movie),
    PhoneNavItem(route = "emby_home", label = "Emby", icon = Icons.Filled.VideoLibrary),
    PhoneNavItem(route = "jellyfin_home", label = "Jellyfin", icon = Icons.Filled.PlayCircleFilled),
    // Search/Favorites tabs join this list in later milestones - the bottom bar already
    // renders a list, so adding an entry is the only change needed there.
)

/**
 * Bottom-nav shell for phone. [bottomBarVisible] lets full-screen destinations (the player)
 * hide the bar without needing a second Scaffold.
 */
@Composable
fun PhoneScaffold(
    currentRoute: String?,
    bottomBarVisible: Boolean,
    onNavigate: (String) -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
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
