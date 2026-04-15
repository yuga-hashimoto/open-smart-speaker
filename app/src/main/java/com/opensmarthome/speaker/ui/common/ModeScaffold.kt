package com.opensmarthome.speaker.ui.common

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.opensmarthome.speaker.R
import com.opensmarthome.speaker.ui.navigation.AppNavigation
import com.opensmarthome.speaker.ui.navigation.AppRoute

private data class BottomNavItem(
    val route: String,
    val icon: ImageVector,
    val labelResId: Int
)

private val bottomNavItems = listOf(
    BottomNavItem(AppRoute.Chat.route, Icons.Filled.Chat, R.string.tab_chat),
    BottomNavItem(AppRoute.Dashboard.route, Icons.Filled.Dashboard, R.string.tab_dashboard),
    BottomNavItem(AppRoute.Ambient.route, Icons.Filled.NightsStay, R.string.tab_ambient),
    BottomNavItem(AppRoute.Settings.route, Icons.Filled.Settings, R.string.tab_settings),
)

@Composable
fun ModeScaffold(
    navController: NavHostController = rememberNavController()
) {
    Scaffold(
        bottomBar = {
            BottomNavigationBar(navController)
        }
    ) { innerPadding ->
        AppNavigation(
            navController = navController,
            modifier = Modifier.padding(innerPadding)
        )
    }
}

@Composable
private fun BottomNavigationBar(navController: NavHostController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    NavigationBar {
        bottomNavItems.forEach { item ->
            val selected = currentDestination?.hierarchy?.any { it.route == item.route } == true
            NavigationBarItem(
                icon = { Icon(item.icon, contentDescription = stringResource(item.labelResId)) },
                label = { Text(stringResource(item.labelResId)) },
                selected = selected,
                onClick = {
                    if (!selected) {
                        navController.navigate(item.route) {
                            popUpTo(navController.graph.startDestinationId) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }
            )
        }
    }
}
