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
        subtitle = "Movies",
        route = Route.VOD_PATTERN,
        icon = Icons.Filled.Movie,
        accent = Palette.Accent,
    ),
    DashboardEntry(
        title = "Jellyfin",
        subtitle = "Your Jellyfin library",
        route = Route.JELLYFIN_HOME_PATTERN,
        icon = Icons.Filled.PlayCircleFilled,
        accent = Palette.SourceJellyfin,
    ),
    DashboardEntry(
        title = "Emby",
        subtitle = "Your Emby library",
        route = Route.EMBY_HOME_PATTERN,
        icon = Icons.Filled.VideoLibrary,
        accent = Palette.SourceEmby,
    ),
)
