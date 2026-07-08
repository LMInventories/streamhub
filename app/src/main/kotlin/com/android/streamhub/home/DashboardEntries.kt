package com.android.streamhub.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.android.streamhub.core.common.nav.Route
import com.android.streamhub.core.design.Palette

data class DashboardEntry(
    val title: String,
    val subtitle: String,
    val route: String,
    val icon: ImageVector,
    val accent: Color,
)

/** Shared by both HomeScreenPhone and HomeScreenTv so the dashboard's content never drifts between form factors. */
val dashboardEntries = listOf(
    DashboardEntry(
        title = "Live TV",
        subtitle = "Channels & EPG",
        route = Route.LIVE_TV_PATTERN,
        icon = Icons.Filled.LiveTv,
        accent = Palette.SourceIptv,
    ),
    DashboardEntry(
        title = "VOD",
        subtitle = "Movies/TV Shows",
        route = Route.VOD_PATTERN,
        icon = Icons.Filled.Movie,
        accent = Palette.Accent,
    ),
    DashboardEntry(
        // Reserved for the connected server's name once Jellyfin is actually wired up
        // (Milestone 3) - "Not connected" is the honest current state, not a placeholder label.
        title = "Jellyfin",
        subtitle = "Not connected",
        route = Route.JELLYFIN_HOME_PATTERN,
        icon = Icons.Filled.PlayCircleFilled,
        accent = Palette.SourceJellyfin,
    ),
    DashboardEntry(
        // Same as Jellyfin above - reserved for the connected server's name once Emby lands (M4).
        title = "Emby",
        subtitle = "Not connected",
        route = Route.EMBY_HOME_PATTERN,
        icon = Icons.Filled.VideoLibrary,
        accent = Palette.SourceEmby,
    ),
)
