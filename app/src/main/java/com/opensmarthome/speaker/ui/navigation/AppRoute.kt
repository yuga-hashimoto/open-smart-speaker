package com.opensmarthome.speaker.ui.navigation

sealed class AppRoute(val route: String) {
    data object Chat : AppRoute("chat")
    data object Dashboard : AppRoute("dashboard")
    data object Ambient : AppRoute("ambient")
    data object Settings : AppRoute("settings")
}
