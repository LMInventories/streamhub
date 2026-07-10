package com.android.streamhub.core.ui.tv.scaffold

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.tv.material3.Tab
import androidx.tv.material3.TabRow
import androidx.tv.material3.Text

data class TvNavItem(val route: String, val label: String)

val tvNavItems = listOf(
    TvNavItem(route = "home", label = "Home"),
    TvNavItem(route = "search", label = "Search"),
    TvNavItem(route = "live_tv", label = "Live TV"),
    TvNavItem(route = "vod", label = "VOD"),
    TvNavItem(route = "emby_home", label = "Emby"),
    TvNavItem(route = "jellyfin_home", label = "Jellyfin"),
    TvNavItem(route = "settings", label = "Settings"),
    // Same list-driven shape as the phone bottom bar - Favorites appends here later.
)

/**
 * Top tab row shell for TV. D-pad focus moves along the tab row (tv-material's TabRow handles
 * the focus/remote-key wiring) and down into whatever [content] renders below it.
 *
 * Navigates only on click/select, not on mere focus. Firing navigation from onFocus (as this used
 * to) sets up a feedback loop: focus moving to a tab navigates -> the route change recomposes
 * this with a new selectedTabIndex -> TabRow reactively re-syncs focus to that index - which can
 * fight the D-pad's own focus movement if that recomposition lands a frame behind, reading as
 * "the tab row doesn't respond" or snapping back to the previous tab. Click-only sidesteps the
 * loop entirely and matches how most Android TV apps' top nav actually behaves (arrow to
 * highlight, press select to actually go there).
 */
@Composable
fun TvScaffold(
    currentRoute: String?,
    tabRowVisible: Boolean,
    onNavigate: (String) -> Unit,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        if (tabRowVisible) {
            val selectedIndex = tvNavItems.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0)

            TabRow(selectedTabIndex = selectedIndex) {
                tvNavItems.forEachIndexed { index, item ->
                    Tab(
                        selected = index == selectedIndex,
                        onClick = { onNavigate(item.route) },
                    ) {
                        Text(text = item.label)
                    }
                }
            }
        }
        content()
    }
}
