package com.android.streamhub.nav

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
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
import com.android.streamhub.core.ui.tv.scaffold.TvScaffold
import com.android.streamhub.downloads.DownloadsManagementScreen
import com.android.streamhub.feature.iptv.livetv.LiveTvScreenTv
import com.android.streamhub.feature.iptv.recordings.RecordingsScreen
import com.android.streamhub.feature.iptv.scheduled.ScheduledManagementScreen
import com.android.streamhub.feature.iptv.settings.IptvAutoUpdateEffect
import com.android.streamhub.feature.iptv.settings.IptvPlaybackSettingsScreen
import com.android.streamhub.feature.iptv.settings.IptvSettingsScreen
import com.android.streamhub.feature.iptv.vod.ItemDetailScreen
import com.android.streamhub.feature.iptv.vod.SeriesDetailScreen
import com.android.streamhub.feature.iptv.vod.VodScreenTv
import com.android.streamhub.feature.jellyfin.detail.JellyfinItemDetailScreen
import com.android.streamhub.feature.jellyfin.detail.JellyfinSeriesDetailScreen
import com.android.streamhub.feature.jellyfin.home.JellyfinHomeScreen
import com.android.streamhub.feature.jellyfin.library.JellyfinFavoritesScreen
import com.android.streamhub.feature.jellyfin.library.JellyfinLibraryScreen
import com.android.streamhub.feature.jellyfin.settings.JellyfinHomeSectionOrderScreen
import com.android.streamhub.feature.jellyfin.settings.JellyfinLibraryVisibilityScreen
import com.android.streamhub.feature.jellyfin.settings.JellyfinPlaybackSettingsScreen
import com.android.streamhub.feature.jellyfin.settings.JellyfinSettingsScreen
import com.android.streamhub.feature.player.PlayerScreenTv
import com.android.streamhub.home.HomeScreenTv
import com.android.streamhub.placeholder.ComingSoonScreen
import com.android.streamhub.search.SearchScreen
import com.android.streamhub.settings.AppUiSettingsScreen
import com.android.streamhub.settings.SettingsScreen

private val TAB_ROUTES = setOf(
    Route.HOME_PATTERN,
    Route.SEARCH_PATTERN,
    Route.LIVE_TV_PATTERN,
    Route.VOD_PATTERN,
    Route.EMBY_HOME_PATTERN,
    Route.JELLYFIN_HOME_PATTERN,
    Route.SETTINGS_PATTERN,
)

@Composable
fun TvApp(navController: NavHostController = rememberNavController()) {
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    // Live TV's mini-preview can expand to fill the screen in place (same player instance, no
    // navigation - see LiveTvScreenTv) - TvScaffold's normal tabRowVisible is route-based and has
    // no way to know about that, so this is the extra signal that actually gets the tab row out
    // of the way for it.
    val isFullscreenOverlayActive by hiltViewModel<FullscreenOverlayViewModel>().isActive.collectAsStateWithLifecycle()

    IptvAutoUpdateEffect()

    // Same "switch tabs, keep each tab's state" pattern as the phone nav host - shared by both the
    // tab row itself and Home's own dashboard tiles, which used to call
    // navController.navigate(route) directly with none of this (see PhoneNavHost's matching
    // comment for the resulting bug: a dashboard tile's destination could get left stuck on top of
    // "home" instead of the tab row's Home button actually returning to it).
    val navigateToTab: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    TvScaffold(
        currentRoute = currentRoute,
        tabRowVisible = currentRoute in TAB_ROUTES && !isFullscreenOverlayActive,
        onNavigate = navigateToTab,
    ) {
        NavHost(navController = navController, startDestination = Route.HOME_PATTERN) {
            composable(Route.HOME_PATTERN) {
                HomeScreenTv(
                    onNavigate = navigateToTab,
                )
            }
            composable(Route.SEARCH_PATTERN) {
                SearchScreen(
                    paddingValues = PaddingValues(24.dp),
                    onPlayChannel = { channelId -> navController.navigate(Route.playerRoute(channelId, SourceType.IPTV)) },
                    onOpenVodMovie = { itemId -> navController.navigate(Route.vodItemDetailRoute(itemId)) },
                    onOpenVodShow = { seriesId -> navController.navigate(Route.vodSeriesDetailRoute(seriesId)) },
                    onOpenJellyfinItem = { item -> navController.navigate(jellyfinDetailRouteFor(item)) },
                )
            }
            composable(Route.LIVE_TV_PATTERN) {
                LiveTvScreenTv(
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
                VodScreenTv(
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
                    paddingValues = PaddingValues(24.dp),
                )
            }
            composable(Route.JELLYFIN_HOME_PATTERN) {
                JellyfinHomeScreen(
                    paddingValues = PaddingValues(24.dp),
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
                    paddingValues = PaddingValues(24.dp),
                    onIptvClick = { navController.navigate(Route.IPTV_SETTINGS_PATTERN) },
                    onJellyfinClick = { navController.navigate(Route.JELLYFIN_SETTINGS_PATTERN) },
                    onJellyfinPlaybackClick = { navController.navigate(Route.JELLYFIN_PLAYBACK_SETTINGS_PATTERN) },
                    onJellyfinLibrariesClick = { navController.navigate(Route.JELLYFIN_LIBRARY_VISIBILITY_PATTERN) },
                    onJellyfinHomeOrderClick = { navController.navigate(Route.JELLYFIN_HOME_SECTION_ORDER_PATTERN) },
                    onAppearanceClick = { navController.navigate(Route.APP_UI_SETTINGS_PATTERN) },
                    onIptvPlaybackClick = { navController.navigate(Route.IPTV_PLAYBACK_SETTINGS_PATTERN) },
                    onScheduledManagementClick = { navController.navigate(Route.SCHEDULED_MANAGEMENT_PATTERN) },
                    onDownloadsManagementClick = { navController.navigate(Route.DOWNLOADS_MANAGEMENT_PATTERN) },
                )
            }
            composable(Route.IPTV_SETTINGS_PATTERN) {
                IptvSettingsScreen(onDone = { navController.popBackStack() })
            }
            composable(Route.IPTV_PLAYBACK_SETTINGS_PATTERN) {
                IptvPlaybackSettingsScreen(onDone = { navController.popBackStack() })
            }
            composable(Route.DOWNLOADS_MANAGEMENT_PATTERN) {
                DownloadsManagementScreen(onBack = { navController.popBackStack() })
            }
            composable(Route.SCHEDULED_MANAGEMENT_PATTERN) {
                ScheduledManagementScreen(onBack = { navController.popBackStack() })
            }
            composable(Route.JELLYFIN_SETTINGS_PATTERN) {
                JellyfinSettingsScreen(onDone = { navController.popBackStack() })
            }
            composable(Route.JELLYFIN_PLAYBACK_SETTINGS_PATTERN) {
                JellyfinPlaybackSettingsScreen(onDone = { navController.popBackStack() })
            }
            composable(Route.JELLYFIN_LIBRARY_VISIBILITY_PATTERN) {
                JellyfinLibraryVisibilityScreen(onDone = { navController.popBackStack() })
            }
            composable(Route.JELLYFIN_HOME_SECTION_ORDER_PATTERN) {
                JellyfinHomeSectionOrderScreen(onDone = { navController.popBackStack() })
            }
            composable(Route.APP_UI_SETTINGS_PATTERN) {
                AppUiSettingsScreen(onDone = { navController.popBackStack() })
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
