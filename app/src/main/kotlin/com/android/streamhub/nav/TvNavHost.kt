package com.android.streamhub.nav

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.android.streamhub.core.common.domain.SourceType
import com.android.streamhub.core.common.nav.Route
import com.android.streamhub.core.ui.tv.scaffold.TvScaffold
import com.android.streamhub.feature.iptv.livetv.LiveTvScreenTv
import com.android.streamhub.feature.iptv.settings.IptvSettingsScreen
import com.android.streamhub.feature.iptv.vod.VodScreenTv
import com.android.streamhub.feature.player.PlayerScreenTv
import com.android.streamhub.home.HomeScreenTv
import com.android.streamhub.placeholder.ComingSoonScreen
import com.android.streamhub.settings.SettingsScreen

private val TAB_ROUTES = setOf(
    Route.HOME_PATTERN,
    Route.LIVE_TV_PATTERN,
    Route.VOD_PATTERN,
    Route.EMBY_HOME_PATTERN,
    Route.JELLYFIN_HOME_PATTERN,
)

@Composable
fun TvApp(navController: NavHostController = rememberNavController()) {
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route

    TvScaffold(
        currentRoute = currentRoute,
        tabRowVisible = currentRoute in TAB_ROUTES,
        onNavigate = { route ->
            // Same "switch tabs, keep each tab's state" pattern as the phone nav host - without
            // this, hopping between tabs would restart the mini-player every time instead of
            // resuming the one already running.
            navController.navigate(route) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        },
    ) {
        NavHost(navController = navController, startDestination = Route.HOME_PATTERN) {
            composable(Route.HOME_PATTERN) {
                HomeScreenTv(
                    onNavigate = { route -> navController.navigate(route) },
                    onSettingsClick = { navController.navigate(Route.SETTINGS_PATTERN) },
                )
            }
            composable(Route.LIVE_TV_PATTERN) {
                LiveTvScreenTv(
                    onSettingsClick = { navController.navigate(Route.IPTV_SETTINGS_PATTERN) },
                    onFullscreen = { channelId ->
                        navController.navigate(Route.playerRoute(channelId, SourceType.IPTV))
                    },
                )
            }
            composable(Route.VOD_PATTERN) {
                VodScreenTv(
                    onSettingsClick = { navController.navigate(Route.IPTV_SETTINGS_PATTERN) },
                    onFullscreen = { itemId ->
                        navController.navigate(Route.playerRoute(itemId, SourceType.IPTV))
                    },
                )
            }
            composable(Route.EMBY_HOME_PATTERN) {
                ComingSoonScreen(
                    title = "Emby",
                    message = "Emby integration isn't wired up yet.",
                    paddingValues = PaddingValues(24.dp),
                    onSettingsClick = { navController.navigate(Route.SETTINGS_PATTERN) },
                )
            }
            composable(Route.JELLYFIN_HOME_PATTERN) {
                ComingSoonScreen(
                    title = "Jellyfin",
                    message = "Jellyfin integration isn't wired up yet.",
                    paddingValues = PaddingValues(24.dp),
                    onSettingsClick = { navController.navigate(Route.SETTINGS_PATTERN) },
                )
            }
            composable(Route.SETTINGS_PATTERN) {
                SettingsScreen(
                    paddingValues = PaddingValues(24.dp),
                    onIptvClick = { navController.navigate(Route.IPTV_SETTINGS_PATTERN) },
                )
            }
            composable(Route.IPTV_SETTINGS_PATTERN) {
                IptvSettingsScreen(onDone = { navController.popBackStack() })
            }
            composable(
                route = Route.PLAYER_PATTERN,
                arguments = listOf(
                    navArgument("sourceType") { type = NavType.StringType },
                    navArgument("itemId") { type = NavType.StringType },
                ),
            ) {
                PlayerScreenTv(onBack = { navController.popBackStack() })
            }
        }
    }
}
