package com.opensmarthome.speaker.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.opensmarthome.speaker.ui.ambient.AmbientScreen
import com.opensmarthome.speaker.ui.chat.ChatScreen
import com.opensmarthome.speaker.ui.dashboard.DashboardScreen
import com.opensmarthome.speaker.ui.settings.SettingsScreen

@Composable
fun AppNavigation(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = AppRoute.Chat.route,
        modifier = modifier
    ) {
        composable(AppRoute.Chat.route) {
            ChatScreen()
        }
        composable(AppRoute.Dashboard.route) {
            DashboardScreen()
        }
        composable(AppRoute.Ambient.route) {
            AmbientScreen()
        }
        composable(AppRoute.Settings.route) {
            SettingsScreen()
        }
    }
}
