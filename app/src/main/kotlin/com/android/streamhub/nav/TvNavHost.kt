package com.android.streamhub.nav

import androidx.compose.runtime.Composable
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.android.streamhub.core.common.domain.SourceType
import com.android.streamhub.core.common.nav.Route
import com.android.streamhub.core.ui.tv.scaffold.TvScaffold
import com.android.streamhub.feature.iptv.livetv.LiveTvScreenTv
import com.android.streamhub.feature.iptv.livetv.epggrid.EpgGridScreen
import com.android.streamhub.feature.iptv.settings.IptvSettingsScreen
import com.android.streamhub.feature.player.PlayerScreenTv
import com.android.streamhub.home.HomeScreenTv

@Composable
fun TvApp(navController: NavHostController = rememberNavController()) {
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route

    TvScaffold(
        currentRoute = currentRoute,
        tabRowVisible = currentRoute == Route.HOME_PATTERN || currentRoute == Route.LIVE_TV_PATTERN,
        onNavigate = { route ->
            // Same "switch tabs, keep each tab's state" pattern as the phone nav host - without
            // this, hopping Home <-> Live TV would restart the mini-player every time instead
            // of resuming the one already running.
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
                    onItemClick = { item ->
                        navController.navigate(Route.playerRoute(item.id, item.sourceType))
                    },
                    onSettingsClick = { navController.navigate(Route.IPTV_SETTINGS_PATTERN) },
                )
            }
            composable(Route.LIVE_TV_PATTERN) {
                LiveTvScreenTv(
                    onSettingsClick = { navController.navigate(Route.IPTV_SETTINGS_PATTERN) },
                    onFullscreen = { channelId ->
                        navController.navigate(Route.playerRoute(channelId, SourceType.IPTV))
                    },
                    onOpenGuide = { categoryId ->
                        navController.navigate(Route.epgGridRoute(categoryId))
                    },
                )
            }
            composable(
                route = Route.EPG_GRID_PATTERN,
                arguments = listOf(navArgument("categoryId") { type = NavType.StringType }),
            ) {
                EpgGridScreen(onBack = { navController.popBackStack() })
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
