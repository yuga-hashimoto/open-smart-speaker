package com.opensmarthome.speaker.tool.system

/**
 * Launches apps on the device by name or package.
 */
interface AppLauncher {
    suspend fun launchApp(appName: String): Boolean
    suspend fun listInstalledApps(): List<AppInfo>
}

data class AppInfo(
    val name: String,
    val packageName: String
)
