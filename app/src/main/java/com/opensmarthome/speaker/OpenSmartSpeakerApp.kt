package com.opensmarthome.speaker

import android.app.Application
import com.opensmarthome.speaker.util.LocaleManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class OpenSmartSpeakerApp : Application() {

    @Inject lateinit var localeManager: LocaleManager

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        // Re-apply saved UI locale before any UI surface spins up — the
        // LocaleManager is an injected Singleton so Hilt's graph is
        // already live by the time Application.onCreate runs.
        appScope.launch { localeManager.applySaved() }
        // ProviderManager.initialize() is called from MainActivity
        // after model download is complete
    }
}
