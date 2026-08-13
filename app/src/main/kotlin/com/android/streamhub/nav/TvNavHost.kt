package com.android.streamhub.nav

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
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
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.android.streamhub.core.common.domain.SourceType
import com.android.streamhub.core.common.nav.Route
import com.android.streamhub.core.design.AppShapes
import com.android.streamhub.core.design.Palette
import com.android.streamhub.core.ui.tv.scaffold.TvScaffold
import com.android.streamhub.downloads.DownloadsManagementScreenTv
import com.android.streamhub.feature.iptv.livetv.LiveTvScreenTv
import com.android.streamhub.feature.iptv.recordings.RecordingsScreen
import com.android.streamhub.feature.iptv.scheduled.ScheduledManagementScreenTv
import com.android.streamhub.feature.iptv.settings.IptvAutoUpdateEffect
import com.android.streamhub.update.AppUpdateCheckEffect
import com.android.streamhub.feature.iptv.settings.IptvPlaybackSettingsScreenTv
import com.android.streamhub.feature.iptv.settings.IptvSettingsScreenTv
import com.android.streamhub.feature.iptv.vod.ItemDetailScreen
import com.android.streamhub.feature.iptv.vod.SeriesDetailScreen
import com.android.streamhub.feature.iptv.vod.VodLibraryScreen
import com.android.streamhub.feature.iptv.vod.VodScreenTv
import com.android.streamhub.feature.emby.detail.EmbyItemDetailScreenTv
import com.android.streamhub.feature.emby.detail.EmbySeriesDetailScreenTv
import com.android.streamhub.feature.emby.home.EmbyHomeScreenTv
import com.android.streamhub.feature.emby.library.EmbyFavoritesScreen
import com.android.streamhub.feature.emby.library.EmbyLibraryScreen
import com.android.streamhub.feature.emby.settings.EmbyHomeSectionOrderScreenTv
import com.android.streamhub.feature.emby.settings.EmbyLibraryVisibilityScreenTv
import com.android.streamhub.feature.emby.settings.EmbyPlaybackSettingsScreenTv
import com.android.streamhub.feature.emby.settings.EmbySettingsScreenTv
import com.android.streamhub.feature.jellyfin.detail.JellyfinItemDetailScreenTv
import com.android.streamhub.feature.jellyfin.detail.JellyfinSeriesDetailScreenTv
import com.android.streamhub.feature.jellyfin.home.JellyfinHomeScreenTv
import com.android.streamhub.feature.jellyfin.library.JellyfinFavoritesScreen
import com.android.streamhub.feature.jellyfin.library.JellyfinLibraryScreen
import com.android.streamhub.feature.jellyfin.library.JellyfinSeeAllScreen
import com.android.streamhub.feature.jellyfin.settings.JellyfinHomeSectionOrderScreenTv
import com.android.streamhub.feature.jellyfin.settings.JellyfinLibraryVisibilityScreenTv
import com.android.streamhub.feature.jellyfin.settings.JellyfinPlaybackSettingsScreenTv
import com.android.streamhub.feature.jellyfin.settings.JellyfinSettingsScreenTv
import com.android.streamhub.feature.player.PlayerScreenTv
import com.android.streamhub.search.SearchScreen
import com.android.streamhub.settings.AppUiSettingsScreenTv
import com.android.streamhub.settings.AppUiSettingsViewModel
import com.android.streamhub.settings.SettingsScreenTv

private val TAB_ROUTES = setOf(
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

    // Explicit history of visited tabs. The popUpTo below is exactly what keeps each tab's
    // ViewModel/mini-player alive across switches, but as a side effect it also collapses
    // NavController's own back stack down to just [start destination, current tab] every time -
    // so System Back, left entirely to its default popBackStack(), always lands on Home the
    // instant you're more than one tab-switch away from it, rather than the tab you were actually
    // on before. This is tracked separately so Back can walk back through visited tabs one at a
    // time instead - reported as "back always returns to Home instead of the previous screen".
    val tabBackHistory = remember { mutableStateListOf<String>() }

    // Same "switch tabs, keep each tab's state" pattern as the phone nav host - shared by both the
    // tab row itself and Home's own dashboard tiles, which used to call
    // navController.navigate(route) directly with none of this (see PhoneNavHost's matching
    // comment for the resulting bug: a dashboard tile's destination could get left stuck on top of
    // "home" instead of the tab row's Home button actually returning to it).
    val navigateToTab: (String) -> Unit = { route ->
        if (route != currentRoute) {
            currentRoute?.let {
                tabBackHistory.remove(it)
                tabBackHistory.add(it)
            }
        }
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    // Pops history directly instead of going through navigateToTab above, which would otherwise
    // record this "going back" hop as if it were a forward navigation and corrupt the history
    // it's reading from.
    val goBackToPreviousTab: () -> Unit = {
        if (tabBackHistory.isNotEmpty()) {
            navController.navigate(tabBackHistory.removeLast()) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    // Back on a tab route used to just no-op (and silently fall through to the Activity's default
    // finish()) once tabBackHistory ran out - reported as "Back closes the app" from e.g. Settings
    // with a row merely focused, not even opened. This is the deterministic 3-step ladder instead:
    // previous tab if any, else open the nav rail (the "second-to-last" back - always somewhere to
    // go before actually leaving), else only then offer to exit. Always enabled (not gated on
    // tabBackHistory like the old per-tab handler was) since every branch here has *something* to
    // do - there's no longer a state where this handler has nothing left to catch.
    var railFocused by remember { mutableStateOf(false) }
    var showExitConfirmation by remember { mutableStateOf(false) }
    val railFocusRequester = remember { FocusRequester() }

    // requestFocus() is called from a LaunchedEffect, not directly inside handleTabBack, even
    // though handleTabBack already runs inside a Composable - handleTabBack itself is invoked
    // synchronously from a raw onPreviewKeyEvent callback several call-frames down (Settings'
    // own key interceptor calling this as onBackFromTopLevel), which is a meaningfully different
    // call context than every other requestFocus() in this app, all of which fire from a
    // LaunchedEffect on the composition's own frame. Deferring this one the same way removes any
    // doubt about calling into the focus system from that raw-key-callback stack.
    var pendingRailFocusRequest by remember { mutableIntStateOf(0) }
    LaunchedEffect(pendingRailFocusRequest) {
        if (pendingRailFocusRequest > 0) {
            runCatching { railFocusRequester.requestFocus() }
        }
    }

    val handleTabBack: () -> Unit = {
        when {
            tabBackHistory.isNotEmpty() -> goBackToPreviousTab()
            !railFocused -> pendingRailFocusRequest++
            else -> showExitConfirmation = true
        }
    }

    if (showExitConfirmation) {
        // Captured here (during composition) rather than read inside the onConfirm lambda itself -
        // LocalContext.current is only safe to read while actually composing, not from inside a
        // callback that fires later on a click, same reasoning as PlayerScreenTv's own activity lookup.
        val activity = LocalContext.current as? Activity
        ExitConfirmationDialog(
            onConfirm = { activity?.finish() },
            onDismiss = { showExitConfirmation = false },
        )
    }

    TvScaffold(
        currentRoute = currentRoute,
        tabRowVisible = currentRoute in TAB_ROUTES && !isFullscreenOverlayActive,
        onNavigate = navigateToTab,
        onRailFocusChanged = { railFocused = it },
        railFocusRequester = railFocusRequester,
    ) {
        NavHost(navController = navController, startDestination = Route.LIVE_TV_PATTERN) {
            composable(Route.SEARCH_PATTERN) {
                BackHandler(onBack = handleTabBack)
                SearchScreen(
                    paddingValues = PaddingValues(24.dp),
                    onPlayChannel = { channelId -> navController.navigate(Route.playerRoute(channelId, SourceType.IPTV)) },
                    onOpenVodMovie = { itemId -> navController.navigate(Route.vodItemDetailRoute(itemId)) },
                    onOpenVodShow = { seriesId -> navController.navigate(Route.vodSeriesDetailRoute(seriesId)) },
                    onOpenJellyfinItem = { item -> navController.navigate(jellyfinDetailRouteFor(item)) },
                    onOpenEmbyItem = { item -> navController.navigate(embyDetailRouteFor(item)) },
                )
            }
            composable(Route.LIVE_TV_PATTERN) {
                BackHandler(onBack = handleTabBack)
                LiveTvScreenTv(
                    onFullscreen = { channelId -> navController.navigate(Route.playerRoute(channelId, SourceType.IPTV)) },
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
                BackHandler(onBack = handleTabBack)
                VodScreenTv(
                    onOpenLibrary = { mode -> navController.navigate(Route.vodLibraryRoute(mode.name)) },
                    onOpenMovie = { itemId -> navController.navigate(Route.vodItemDetailRoute(itemId)) },
                    onOpenShow = { seriesId -> navController.navigate(Route.vodSeriesDetailRoute(seriesId)) },
                )
            }
            composable(
                route = Route.VOD_LIBRARY_PATTERN,
                arguments = listOf(navArgument("mode") { type = NavType.StringType }),
            ) {
                VodLibraryScreen(
                    onBack = { navController.popBackStack() },
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
                BackHandler(onBack = handleTabBack)
                EmbyHomeScreenTv(
                    onOpenLibrary = { libraryId, itemType ->
                        navController.navigate(Route.embyLibraryRoute(libraryId, itemType.name))
                    },
                    onOpenItem = { item -> navController.navigate(embyDetailRouteFor(item)) },
                    onSignInClick = { navController.navigate(Route.EMBY_SETTINGS_PATTERN) },
                    onOpenFavorites = { navController.navigate(Route.EMBY_FAVORITES_PATTERN) },
                )
            }
            composable(Route.EMBY_FAVORITES_PATTERN) {
                EmbyFavoritesScreen(
                    onBack = { navController.popBackStack() },
                    onOpenItem = { item -> navController.navigate(embyDetailRouteFor(item)) },
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
                EmbyItemDetailScreenTv(
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
                EmbySeriesDetailScreenTv(
                    seriesId = seriesId,
                    onPlayEpisode = { episodeId -> navController.navigate(Route.playerRoute(episodeId, SourceType.EMBY)) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Route.EMBY_SETTINGS_PATTERN) {
                EmbySettingsScreenTv(onDone = { navController.popBackStack() })
            }
            composable(Route.EMBY_PLAYBACK_SETTINGS_PATTERN) {
                EmbyPlaybackSettingsScreenTv(onDone = { navController.popBackStack() })
            }
            composable(Route.EMBY_LIBRARY_VISIBILITY_PATTERN) {
                EmbyLibraryVisibilityScreenTv(onDone = { navController.popBackStack() })
            }
            composable(Route.EMBY_HOME_SECTION_ORDER_PATTERN) {
                EmbyHomeSectionOrderScreenTv(onDone = { navController.popBackStack() })
            }
            composable(Route.JELLYFIN_HOME_PATTERN) {
                BackHandler(onBack = handleTabBack)
                JellyfinHomeScreenTv(
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
                JellyfinItemDetailScreenTv(
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
                JellyfinSeriesDetailScreenTv(
                    onBack = { navController.popBackStack() },
                    onOpenEpisode = { itemId -> navController.navigate(Route.jellyfinItemDetailRoute(itemId)) },
                    onOpenSeries = { seriesId -> navController.navigate(Route.jellyfinSeriesDetailRoute(seriesId)) },
                )
            }
            composable(Route.SETTINGS_PATTERN) {
                // No standalone BackHandler here - SettingsScreenTv owns Back entirely for this
                // route (detail-pane-to-section-tabs internally, escalating to onBackFromTopLevel
                // otherwise) via a single handler, rather than a second independently-registered
                // one here whose priority relative to that inner one isn't worth depending on.
                SettingsScreenTv(
                    onIptvClick = { navController.navigate(Route.IPTV_SETTINGS_PATTERN) },
                    onEmbyClick = { navController.navigate(Route.EMBY_SETTINGS_PATTERN) },
                    onEmbyPlaybackClick = { navController.navigate(Route.EMBY_PLAYBACK_SETTINGS_PATTERN) },
                    onEmbyLibrariesClick = { navController.navigate(Route.EMBY_LIBRARY_VISIBILITY_PATTERN) },
                    onEmbyHomeOrderClick = { navController.navigate(Route.EMBY_HOME_SECTION_ORDER_PATTERN) },
                    onJellyfinClick = { navController.navigate(Route.JELLYFIN_SETTINGS_PATTERN) },
                    onJellyfinPlaybackClick = { navController.navigate(Route.JELLYFIN_PLAYBACK_SETTINGS_PATTERN) },
                    onJellyfinLibrariesClick = { navController.navigate(Route.JELLYFIN_LIBRARY_VISIBILITY_PATTERN) },
                    onJellyfinHomeOrderClick = { navController.navigate(Route.JELLYFIN_HOME_SECTION_ORDER_PATTERN) },
                    onAppearanceClick = { navController.navigate(Route.APP_UI_SETTINGS_PATTERN) },
                    onIptvPlaybackClick = { navController.navigate(Route.IPTV_PLAYBACK_SETTINGS_PATTERN) },
                    onScheduledManagementClick = { navController.navigate(Route.SCHEDULED_MANAGEMENT_PATTERN) },
                    onDownloadsManagementClick = { navController.navigate(Route.DOWNLOADS_MANAGEMENT_PATTERN) },
                    onBackFromTopLevel = handleTabBack,
                )
            }
            composable(Route.IPTV_SETTINGS_PATTERN) {
                IptvSettingsScreenTv(onDone = { navController.popBackStack() })
            }
            composable(Route.IPTV_PLAYBACK_SETTINGS_PATTERN) {
                IptvPlaybackSettingsScreenTv(onDone = { navController.popBackStack() })
            }
            composable(Route.DOWNLOADS_MANAGEMENT_PATTERN) {
                DownloadsManagementScreenTv(
                    onBack = { navController.popBackStack() },
                    onOpenDownload = { itemId, sourceType -> navController.navigate(Route.playerRoute(itemId, sourceType)) },
                )
            }
            composable(Route.SCHEDULED_MANAGEMENT_PATTERN) {
                ScheduledManagementScreenTv(onBack = { navController.popBackStack() })
            }
            composable(Route.JELLYFIN_SETTINGS_PATTERN) {
                JellyfinSettingsScreenTv(onDone = { navController.popBackStack() })
            }
            composable(Route.JELLYFIN_PLAYBACK_SETTINGS_PATTERN) {
                JellyfinPlaybackSettingsScreenTv(onDone = { navController.popBackStack() })
            }
            composable(Route.JELLYFIN_LIBRARY_VISIBILITY_PATTERN) {
                JellyfinLibraryVisibilityScreenTv(onDone = { navController.popBackStack() })
            }
            composable(Route.JELLYFIN_HOME_SECTION_ORDER_PATTERN) {
                JellyfinHomeSectionOrderScreenTv(onDone = { navController.popBackStack() })
            }
            composable(Route.APP_UI_SETTINGS_PATTERN) {
                AppUiSettingsScreenTv(onDone = { navController.popBackStack() })
            }
            composable(
                route = Route.PLAYER_PATTERN,
                arguments = listOf(
                    navArgument("sourceType") { type = NavType.StringType },
                    navArgument("itemId") { type = NavType.StringType },
                ),
            ) {
                // AppUiSettingsViewModel rather than a player-module settings repo of its own -
                // matchRefreshRate lives on the same shared AppUiSettings every other appearance
                // toggle does, and :app (unlike feature-player-screen) can already read it.
                val appUiSettingsViewModel: AppUiSettingsViewModel = hiltViewModel()
                val appUiState by appUiSettingsViewModel.uiState.collectAsStateWithLifecycle()
                PlayerScreenTv(onBack = { navController.popBackStack() }, matchRefreshRate = appUiState.matchRefreshRate)
            }
        }
    }
}

/**
 * The true last step of the Back ladder - only reached once there's no previous tab to return to
 * and the nav rail is already focused. Dismiss-on-Back is handled by an explicit raw key
 * interceptor on the dialog's own content, not Dialog's default DialogProperties(dismissOnBackPress
 * = true) - that default relies on the same OnBackPressedDispatcher-based mechanism that turned
 * out to be unreliable for Settings' own Back ladder (see SettingsScreenTv's matching comment), so
 * this dialog gets the same guaranteed-deterministic treatment rather than trusting it works here
 * either. Initial D-pad focus lands on Cancel, not Exit, the same "don't default onto the
 * destructive option" reasoning as every other confirmation-style choice in this app.
 */
@Composable
private fun ExitConfirmationDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        val cancelFocusRequester = remember { FocusRequester() }
        LaunchedEffect(Unit) { runCatching { cancelFocusRequester.requestFocus() } }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .width(360.dp)
                .clip(AppShapes.large)
                .background(Palette.Surface)
                .padding(24.dp)
                .onPreviewKeyEvent { event ->
                    if (event.key != Key.Back || event.type != KeyEventType.KeyUp) return@onPreviewKeyEvent false
                    onDismiss()
                    true
                },
        ) {
            Text(text = "Exit StreamHub?", color = Palette.TextPrimary, style = MaterialTheme.typography.titleLarge)
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(top = 20.dp),
            ) {
                Button(onClick = onDismiss, modifier = Modifier.focusRequester(cancelFocusRequester)) {
                    Text("Cancel")
                }
                Button(onClick = onConfirm) {
                    Text("Exit")
                }
            }
        }
    }
}
