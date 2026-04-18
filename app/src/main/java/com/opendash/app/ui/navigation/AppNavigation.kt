package com.opendash.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.opendash.app.ui.settings.SettingsScreen
import com.opendash.app.ui.settings.multiroom.SettingsSpeakerGroupsScreen

@Composable
fun AppNavigation(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = AppRoute.Settings.route,
        modifier = modifier
    ) {
        composable(AppRoute.Settings.route) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenSpeakerGroups = { navController.navigate(AppRoute.SpeakerGroups.route) }
            )
        }
        composable(AppRoute.SpeakerGroups.route) {
            SettingsSpeakerGroupsScreen(onBack = { navController.popBackStack() })
        }
    }
}
