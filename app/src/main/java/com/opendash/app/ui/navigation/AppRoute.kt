package com.opendash.app.ui.navigation

sealed class AppRoute(val route: String) {
    data object Home : AppRoute("home")
    data object Devices : AppRoute("devices")
    data object Settings : AppRoute("settings")
    data object SpeakerGroups : AppRoute("settings/speaker-groups")
}
