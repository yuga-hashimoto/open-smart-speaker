package com.opensmarthome.speaker.tool.system

import android.content.Context
import android.content.Intent
import timber.log.Timber

/**
 * Android implementation of AppLauncher using PackageManager.
 *
 * [launchApp] resolves the user's spoken target via [AppNameMatcher]'s fuzzy
 * scoring so utterances like "open the weather app" or "天気アプリ開いて" find
 * the right package even when the user's wording doesn't exactly match the
 * label. Exact match always beats fuzzy; below the threshold we refuse so a
 * mumble doesn't randomly launch Netflix.
 */
class AndroidAppLauncher(
    private val context: Context
) : AppLauncher {

    override suspend fun launchApp(appName: String): Boolean {
        val pm = context.packageManager
        val apps = listInstalledApps()
        val match = AppNameMatcher.findBest(appName, apps) ?: return false

        val launchIntent = pm.getLaunchIntentForPackage(match.packageName)
            ?: return false

        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(launchIntent)
            Timber.d("Launched app: ${match.name} (${match.packageName})")
            true
        } catch (e: Exception) {
            Timber.e(e, "Failed to launch ${match.packageName}")
            false
        }
    }

    override suspend fun listInstalledApps(): List<AppInfo> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        return pm.queryIntentActivities(intent, 0).mapNotNull { resolveInfo ->
            val appName = resolveInfo.loadLabel(pm)?.toString() ?: return@mapNotNull null
            val packageName = resolveInfo.activityInfo?.packageName ?: return@mapNotNull null
            AppInfo(name = appName, packageName = packageName)
        }.distinctBy { it.packageName }
    }
}
