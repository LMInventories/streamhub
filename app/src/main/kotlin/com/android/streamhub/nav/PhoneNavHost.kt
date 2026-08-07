package com.android.streamhub.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.android.streamhub.feature.iptv.scheduled.ScheduledManagementScreen
import com.android.streamhub.feature.iptv.settings.IptvAutoUpdateEffect
import com.android.streamhub.update.AppUpdateCheckEffect
import com.android.streamhub.feature.iptv.settings.IptvPlaybackSettingsScreen
import com.android.streamhub.feature.iptv.settings.IptvSettingsScreen
import com.android.streamhub.feature.iptv.vod.ItemDetailScreen
import com.android.streamhub.feature.iptv.vod.SeriesDetailScreen
import com.android.streamhub.feature.iptv.vod.VodScreenPhone
import com.android.streamhub.feature.emby.detail.EmbyItemDetailScreen
import com.android.streamhub.feature.emby.detail.EmbySeriesDetailScreen
import com.android.streamhub.feature.emby.home.EmbyHomeScreen
import com.android.streamhub.feature.emby.library.EmbyLibraryScreen
import com.android.streamhub.feature.emby.settings.EmbySettingsScreen
import com.android.streamhub.feature.jellyfin.detail.JellyfinItemDetailScreen
import com.android.streamhub.feature.jellyfin.detail.JellyfinSeriesDetailScreen
import com.android.streamhub.feature.jellyfin.home.JellyfinHomeScreen
import com.android.streamhub.feature.jellyfin.library.JellyfinFavoritesScreen
import com.android.streamhub.feature.jellyfin.library.JellyfinLibraryScreen
import com.android.streamhub.feature.jellyfin.library.JellyfinSeeAllScreen
import com.android.streamhub.feature.jellyfin.settings.JellyfinHomeSectionOrderScreen
import com.android.streamhub.feature.jellyfin.settings.JellyfinLibraryVisibilityScreen
import com.android.streamhub.feature.jellyfin.settings.JellyfinPlaybackSettingsScreen
import com.android.streamhub.feature.jellyfin.settings.JellyfinSettingsScreen
import com.android.streamhub.downloads.DownloadsManagementScreen
import com.android.streamhub.feature.player.PlayerScreenPhone
import com.android.streamhub.search.SearchScreen
import com.android.streamhub.settings.AppUiSettingsScreen
import com.android.streamhub.settings.SettingsScreen

private val TAB_ROUTES = setOf(
    Route.SEARCH_PATTERN,
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
    AppUpdateCheckEffect()

    // "On App Launch" (Settings > Appearance) - fires at most once per process (see
    // AppLaunchState), so resuming from background never re-triggers it. Route.LIVE_TV_PATTERN is
    // already this NavHost's own startDestination, so that setting needs no redirect at all.
    val launchRedirectViewModel: AppLaunchRedirectViewModel = hiltViewModel()
    LaunchedEffect(Unit) {
        launchRedirectViewModel.resolveRedirectRouteIfNeeded()?.let { route ->
            navController.navigate(route) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    // Standard bottom-nav "switch tabs, keep each tab's state" pattern - without
    // saveState/restoreState, hopping between tabs would push a fresh backstack entry (and a
    // fresh ViewModel/mini-player) every time instead of resuming the one already running.
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
        NavHost(navController = navController, startDestination = Route.LIVE_TV_PATTERN) {
            composable(Route.SEARCH_PATTERN) {
                SearchScreen(
                    paddingValues = paddingValues,
                    onPlayChannel = { channelId -> navController.navigate(Route.playerRoute(channelId, SourceType.IPTV)) },
                    onOpenVodMovie = { itemId -> navController.navigate(Route.vodItemDetailRoute(itemId)) },
                    onOpenVodShow = { seriesId -> navController.navigate(Route.vodSeriesDetailRoute(seriesId)) },
                    onOpenJellyfinItem = { item -> navController.navigate(jellyfinDetailRouteFor(item)) },
                    onOpenEmbyItem = { item -> navController.navigate(embyDetailRouteFor(item)) },
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
                EmbyHomeScreen(
                    paddingValues = paddingValues,
                    onOpenLibrary = { libraryId, itemType ->
                        navController.navigate(Route.embyLibraryRoute(libraryId, itemType.name))
                    },
                    onOpenItem = { item -> navController.navigate(embyDetailRouteFor(item)) },
                    onSignInClick = { navController.navigate(Route.EMBY_SETTINGS_PATTERN) },
                )
            }
            composable(
                route = Route.EMBY_LIBRARY_PATTERN,
                arguments = listOf(
                    navArgument("libraryId") { type = NavType.StringType },
                    navArgument("itemType") { type = NavType.StringType },
                ),
            ) {
                EmbyLibraryScreen(
                    onBack = { navController.popBackStack() },
                    onOpenItem = { item -> navController.navigate(embyDetailRouteFor(item)) },
                )
            }
            composable(
                route = Route.EMBY_ITEM_DETAIL_PATTERN,
                arguments = listOf(navArgument("itemId") { type = NavType.StringType }),
            ) { backStackEntry ->
                val itemId = checkNotNull(backStackEntry.arguments?.getString("itemId"))
                EmbyItemDetailScreen(
                    itemId = itemId,
                    onBack = { navController.popBackStack() },
                    onPlay = { id -> navController.navigate(Route.playerRoute(id, SourceType.EMBY)) },
                )
            }
            composable(
                route = Route.EMBY_SERIES_DETAIL_PATTERN,
                arguments = listOf(navArgument("seriesId") { type = NavType.StringType }),
            ) { backStackEntry ->
                val seriesId = checkNotNull(backStackEntry.arguments?.getString("seriesId"))
                EmbySeriesDetailScreen(
                    seriesId = seriesId,
                    onPlayEpisode = { episodeId -> navController.navigate(Route.playerRoute(episodeId, SourceType.EMBY)) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Route.EMBY_SETTINGS_PATTERN) {
                EmbySettingsScreen(onDone = { navController.popBackStack() })
            }
            composable(Route.JELLYFIN_HOME_PATTERN) {
                JellyfinHomeScreen(
                    paddingValues = paddingValues,
                    onOpenLibrary = { library ->
                        navController.navigate(Route.jellyfinLibraryRoute(library.id, jellyfinItemTypeFor(library).name))
                    },
                    onOpenItem = { item -> navController.navigate(jellyfinDetailRouteFor(item)) },
                    onOpenFavorites = { navController.navigate(Route.JELLYFIN_FAVORITES_PATTERN) },
                    onOpenSeeAll = { kind -> navController.navigate(Route.jellyfinSeeAllRoute(kind)) },
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
                route = Route.JELLYFIN_SEE_ALL_PATTERN,
                arguments = listOf(navArgument("kind") { type = NavType.StringType }),
            ) {
                JellyfinSeeAllScreen(
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
                    onOpenSeries = { seriesId -> navController.navigate(Route.jellyfinSeriesDetailRoute(seriesId)) },
                    onOpenEpisode = { episodeId -> navController.navigate(Route.jellyfinItemDetailRoute(episodeId)) },
                )
            }
            composable(
                route = Route.JELLYFIN_SERIES_DETAIL_PATTERN,
                arguments = listOf(navArgument("seriesId") { type = NavType.StringType }),
            ) {
                JellyfinSeriesDetailScreen(
                    onBack = { navController.popBackStack() },
                    onOpenEpisode = { itemId -> navController.navigate(Route.jellyfinItemDetailRoute(itemId)) },
                    onOpenSeries = { seriesId -> navController.navigate(Route.jellyfinSeriesDetailRoute(seriesId)) },
                )
            }
            composable(Route.SETTINGS_PATTERN) {
                SettingsScreen(
                    paddingValues = paddingValues,
                    onIptvClick = { navController.navigate(Route.IPTV_SETTINGS_PATTERN) },
                    onEmbyClick = { navController.navigate(Route.EMBY_SETTINGS_PATTERN) },
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
            composable(Route.SCHEDULED_MANAGEMENT_PATTERN) {
                ScheduledManagementScreen(onBack = { navController.popBackStack() })
            }
            composable(Route.DOWNLOADS_MANAGEMENT_PATTERN) {
                DownloadsManagementScreen(
                    onBack = { navController.popBackStack() },
                    onOpenDownload = { itemId, sourceType -> navController.navigate(Route.playerRoute(itemId, sourceType)) },
                )
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
                PlayerScreenPhone(onBack = { navController.popBackStack() })
            }
        }
    }
}
