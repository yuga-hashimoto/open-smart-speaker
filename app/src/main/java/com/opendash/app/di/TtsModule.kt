package com.opendash.app.di

import android.content.Context
import com.opendash.app.data.preferences.AppPreferences
import com.opendash.app.data.preferences.SecurePreferences
import com.opendash.app.voice.tts.TextToSpeech
import com.opendash.app.voice.tts.TtsManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Singleton

/**
 * TextToSpeech binding lives in its own module so instrumented tests can
 * swap the implementation via `@TestInstallIn(replaces = [TtsModule::class])`
 * without rebuilding the rest of the voice graph (STT, VoicePipeline,
 * LatencyRecorder).
 *
 * See `app/src/androidTest/.../FakeTtsTestModule.kt`.
 */
@Module
@InstallIn(SingletonComponent::class)
object TtsModule {

    @Provides
    @Singleton
    fun provideTextToSpeech(
        @ApplicationContext context: Context,
        preferences: AppPreferences,
        securePreferences: SecurePreferences,
        httpClient: OkHttpClient
    ): TextToSpeech = TtsManager(context, preferences, securePreferences, httpClient)
}
