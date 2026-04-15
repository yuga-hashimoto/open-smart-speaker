package com.opensmarthome.speaker

import android.app.Application
import com.opensmarthome.speaker.assistant.provider.ProviderManager
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class OpenSmartSpeakerApp : Application() {

    @Inject lateinit var providerManager: ProviderManager

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        providerManager.initialize()
    }
}
