package com.android.streamhub.nav

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.android.streamhub.core.common.nav.Route
import com.android.streamhub.core.ui.tv.scaffold.TvScaffold
import com.android.streamhub.feature.player.PlayerScreenTv
import com.android.streamhub.home.HomeScreenTv

@Composable
fun TvApp(navController: NavHostController = rememberNavController()) {
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route

    TvScaffold(
        currentRoute = currentRoute,
        tabRowVisible = currentRoute == Route.HOME_PATTERN,
        onNavigate = { route ->
            navController.navigate(route) { launchSingleTop = true }
        },
    ) {
        NavHost(navController = navController, startDestination = Route.HOME_PATTERN) {
            composable(Route.HOME_PATTERN) {
                HomeScreenTv(
                    onItemClick = { item ->
                        navController.navigate(Route.playerRoute(item.id, item.sourceType))
                    },
                )
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
