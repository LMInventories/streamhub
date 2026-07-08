package com.android.streamhub.nav

import androidx.compose.runtime.Composable
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
import com.android.streamhub.core.ui.phone.scaffold.PhoneScaffold
import com.android.streamhub.feature.iptv.livetv.LiveTvScreenPhone
import com.android.streamhub.feature.iptv.settings.IptvAutoUpdateEffect
import com.android.streamhub.feature.iptv.settings.IptvSettingsScreen
import com.android.streamhub.feature.iptv.vod.ItemDetailScreen
import com.android.streamhub.feature.iptv.vod.SeriesDetailScreen
import com.android.streamhub.feature.iptv.vod.VodScreenPhone
import com.android.streamhub.feature.player.PlayerScreenPhone
import com.android.streamhub.home.HomeScreenPhone
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
fun PhoneApp(navController: NavHostController = rememberNavController()) {
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route

    IptvAutoUpdateEffect()

    PhoneScaffold(
        currentRoute = currentRoute,
        bottomBarVisible = currentRoute in TAB_ROUTES,
        onNavigate = { route ->
            // Standard bottom-nav "switch tabs, keep each tab's state" pattern - without
            // saveState/restoreState, hopping between tabs would push a fresh backstack entry
            // (and a fresh ViewModel/mini-player) every time instead of resuming the one already
            // running.
            navController.navigate(route) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        },
    ) { paddingValues ->
        NavHost(navController = navController, startDestination = Route.HOME_PATTERN) {
            composable(Route.HOME_PATTERN) {
                HomeScreenPhone(
                    paddingValues = paddingValues,
                    onNavigate = { route -> navController.navigate(route) },
                    onSettingsClick = { navController.navigate(Route.SETTINGS_PATTERN) },
                )
            }
            composable(Route.LIVE_TV_PATTERN) {
                LiveTvScreenPhone(
                    paddingValues = paddingValues,
                    onSettingsClick = { navController.navigate(Route.IPTV_SETTINGS_PATTERN) },
                    onFullscreen = { channelId ->
                        navController.navigate(Route.playerRoute(channelId, SourceType.IPTV))
                    },
                )
            }
            composable(Route.VOD_PATTERN) {
                VodScreenPhone(
                    paddingValues = paddingValues,
                    onSettingsClick = { navController.navigate(Route.IPTV_SETTINGS_PATTERN) },
                    onOpenMovie = { itemId -> navController.navigate(Route.vodItemDetailRoute(itemId)) },
                    onOpenShow = { seriesId -> navController.navigate(Route.vodSeriesDetailRoute(seriesId)) },
                )
            }
            composable(
                route = Route.VOD_ITEM_DETAIL_PATTERN,
                arguments = listOf(navArgument("itemId") { type = NavType.StringType }),
            ) { backStackEntry ->
                val itemId = checkNotNull(backStackEntry.arguments?.getString("itemId"))
                ItemDetailScreen(
                    onBack = { navController.popBackStack() },
                    onPlay = { navController.navigate(Route.playerRoute(itemId, SourceType.IPTV)) },
                )
            }
            composable(
                route = Route.VOD_SERIES_DETAIL_PATTERN,
                arguments = listOf(navArgument("seriesId") { type = NavType.StringType }),
            ) {
                SeriesDetailScreen(
                    onBack = { navController.popBackStack() },
                    onOpenEpisode = { itemId -> navController.navigate(Route.vodItemDetailRoute(itemId)) },
                )
            }
            composable(Route.EMBY_HOME_PATTERN) {
                ComingSoonScreen(
                    title = "Emby",
                    message = "Emby integration isn't wired up yet.",
                    paddingValues = paddingValues,
                    onSettingsClick = { navController.navigate(Route.SETTINGS_PATTERN) },
                )
            }
            composable(Route.JELLYFIN_HOME_PATTERN) {
                ComingSoonScreen(
                    title = "Jellyfin",
                    message = "Jellyfin integration isn't wired up yet.",
                    paddingValues = paddingValues,
                    onSettingsClick = { navController.navigate(Route.SETTINGS_PATTERN) },
                )
            }
            composable(Route.SETTINGS_PATTERN) {
                SettingsScreen(
                    paddingValues = paddingValues,
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
                PlayerScreenPhone(onBack = { navController.popBackStack() })
            }
        }
    }
}
