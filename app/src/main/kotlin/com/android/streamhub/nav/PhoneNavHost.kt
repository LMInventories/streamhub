package com.android.streamhub.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.android.streamhub.feature.iptv.recordings.RecordingsScreen
import com.android.streamhub.feature.iptv.settings.IptvAutoUpdateEffect
import com.android.streamhub.feature.iptv.settings.IptvSettingsScreen
import com.android.streamhub.feature.iptv.vod.ItemDetailScreen
import com.android.streamhub.feature.iptv.vod.SeriesDetailScreen
import com.android.streamhub.feature.iptv.vod.VodScreenPhone
import com.android.streamhub.feature.jellyfin.detail.JellyfinItemDetailScreen
import com.android.streamhub.feature.jellyfin.detail.JellyfinSeriesDetailScreen
import com.android.streamhub.feature.jellyfin.home.JellyfinHomeScreen
import com.android.streamhub.feature.jellyfin.library.JellyfinFavoritesScreen
import com.android.streamhub.feature.jellyfin.library.JellyfinLibraryScreen
import com.android.streamhub.feature.jellyfin.settings.JellyfinSettingsScreen
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
    Route.SETTINGS_PATTERN,
)

@Composable
fun PhoneApp(navController: NavHostController = rememberNavController()) {
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    // Live TV's mini-preview can expand to fill the screen in place (same player instance, no
    // navigation - see LiveTvScreenPhone) - PhoneScaffold's normal bottomBarVisible is route-based
    // and has no way to know about that, so this is the extra signal that actually gets the bar
    // out of the way for it.
    val isFullscreenOverlayActive by hiltViewModel<FullscreenOverlayViewModel>().isActive.collectAsStateWithLifecycle()

    IptvAutoUpdateEffect()

    // Standard bottom-nav "switch tabs, keep each tab's state" pattern - without
    // saveState/restoreState, hopping between tabs would push a fresh backstack entry (and a
    // fresh ViewModel/mini-player) every time instead of resuming the one already running. Shared
    // by both the bottom nav bar itself and Home's own dashboard tiles - those used to call
    // navController.navigate(route) directly with none of this, which is why tapping a dashboard
    // tile then later tapping Home in the bottom bar could leave a stale/duplicate "home" entry
    // buried under the tile's destination instead of actually returning to it.
    val navigateToTab: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    PhoneScaffold(
        currentRoute = currentRoute,
        bottomBarVisible = currentRoute in TAB_ROUTES && !isFullscreenOverlayActive,
        onNavigate = navigateToTab,
    ) { paddingValues ->
        NavHost(navController = navController, startDestination = Route.HOME_PATTERN) {
            composable(Route.HOME_PATTERN) {
                HomeScreenPhone(
                    paddingValues = paddingValues,
                    onNavigate = navigateToTab,
                )
            }
            composable(Route.LIVE_TV_PATTERN) {
                LiveTvScreenPhone(
                    paddingValues = paddingValues,
                    onFullscreen = { channelId ->
                        navController.navigate(Route.playerRoute(channelId, SourceType.IPTV))
                    },
                    onOpenRecordings = { navController.navigate(Route.RECORDINGS_PATTERN) },
                )
            }
            composable(Route.RECORDINGS_PATTERN) {
                RecordingsScreen(
                    onBack = { navController.popBackStack() },
                    onPlayRecording = { itemId -> navController.navigate(Route.playerRoute(itemId, SourceType.RECORDING)) },
                )
            }
            composable(Route.VOD_PATTERN) {
                VodScreenPhone(
                    paddingValues = paddingValues,
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
                )
            }
            composable(Route.JELLYFIN_HOME_PATTERN) {
                JellyfinHomeScreen(
                    paddingValues = paddingValues,
                    onOpenLibrary = { library ->
                        navController.navigate(Route.jellyfinLibraryRoute(library.id, jellyfinItemTypeFor(library).name))
                    },
                    onOpenItem = { item -> navController.navigate(jellyfinDetailRouteFor(item)) },
                    onOpenFavorites = { navController.navigate(Route.JELLYFIN_FAVORITES_PATTERN) },
                )
            }
            composable(
                route = Route.JELLYFIN_LIBRARY_PATTERN,
                arguments = listOf(
                    navArgument("libraryId") { type = NavType.StringType },
                    navArgument("itemType") { type = NavType.StringType },
                ),
            ) {
                JellyfinLibraryScreen(
                    onBack = { navController.popBackStack() },
                    onOpenItem = { item -> navController.navigate(jellyfinDetailRouteFor(item)) },
                )
            }
            composable(Route.JELLYFIN_FAVORITES_PATTERN) {
                JellyfinFavoritesScreen(
                    onBack = { navController.popBackStack() },
                    onOpenItem = { item -> navController.navigate(jellyfinDetailRouteFor(item)) },
                )
            }
            composable(
                route = Route.JELLYFIN_ITEM_DETAIL_PATTERN,
                arguments = listOf(navArgument("itemId") { type = NavType.StringType }),
            ) { backStackEntry ->
                val itemId = checkNotNull(backStackEntry.arguments?.getString("itemId"))
                JellyfinItemDetailScreen(
                    onBack = { navController.popBackStack() },
                    onPlay = { navController.navigate(Route.playerRoute(itemId, SourceType.JELLYFIN)) },
                )
            }
            composable(
                route = Route.JELLYFIN_SERIES_DETAIL_PATTERN,
                arguments = listOf(navArgument("seriesId") { type = NavType.StringType }),
            ) {
                JellyfinSeriesDetailScreen(
                    onBack = { navController.popBackStack() },
                    onOpenEpisode = { itemId -> navController.navigate(Route.jellyfinItemDetailRoute(itemId)) },
                )
            }
            composable(Route.SETTINGS_PATTERN) {
                SettingsScreen(
                    paddingValues = paddingValues,
                    onIptvClick = { navController.navigate(Route.IPTV_SETTINGS_PATTERN) },
                    onJellyfinClick = { navController.navigate(Route.JELLYFIN_SETTINGS_PATTERN) },
                )
            }
            composable(Route.IPTV_SETTINGS_PATTERN) {
                IptvSettingsScreen(onDone = { navController.popBackStack() })
            }
            composable(Route.JELLYFIN_SETTINGS_PATTERN) {
                JellyfinSettingsScreen(onDone = { navController.popBackStack() })
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
