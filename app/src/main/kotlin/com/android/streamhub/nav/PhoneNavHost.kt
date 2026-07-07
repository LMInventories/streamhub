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
import com.android.streamhub.core.ui.phone.scaffold.PhoneScaffold
import com.android.streamhub.feature.player.PlayerScreenPhone
import com.android.streamhub.home.HomeScreenPhone

@Composable
fun PhoneApp(navController: NavHostController = rememberNavController()) {
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route

    PhoneScaffold(
        currentRoute = currentRoute,
        bottomBarVisible = currentRoute == Route.HOME_PATTERN,
        onNavigate = { route ->
            navController.navigate(route) { launchSingleTop = true }
        },
    ) { paddingValues ->
        NavHost(navController = navController, startDestination = Route.HOME_PATTERN) {
            composable(Route.HOME_PATTERN) {
                HomeScreenPhone(
                    paddingValues = paddingValues,
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
                PlayerScreenPhone(onBack = { navController.popBackStack() })
            }
        }
    }
}
