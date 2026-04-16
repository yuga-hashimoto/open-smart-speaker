package com.opensmarthome.speaker.di

import android.content.Context
import com.opensmarthome.speaker.assistant.router.ConversationRouter
import com.opensmarthome.speaker.data.db.MessageDao
import com.opensmarthome.speaker.data.db.SessionDao
import com.opensmarthome.speaker.data.preferences.AppPreferences
import com.opensmarthome.speaker.tool.ToolExecutor
import com.opensmarthome.speaker.voice.fastpath.FastPathRouter
import com.opensmarthome.speaker.voice.metrics.LatencyRecorder
import com.opensmarthome.speaker.voice.pipeline.VoicePipeline
import com.opensmarthome.speaker.voice.stt.AndroidSttProvider
import com.opensmarthome.speaker.voice.stt.SpeechToText
import com.opensmarthome.speaker.data.preferences.SecurePreferences
import com.opensmarthome.speaker.voice.tts.TextToSpeech
import com.opensmarthome.speaker.voice.tts.TtsManager
import com.squareup.moshi.Moshi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object VoiceModule {

    @Provides
    @Singleton
    fun provideSpeechToText(@ApplicationContext context: Context): SpeechToText =
        AndroidSttProvider(context)

    @Provides
    @Singleton
    fun provideTextToSpeech(
        @ApplicationContext context: Context,
        preferences: AppPreferences,
        securePreferences: SecurePreferences,
        httpClient: OkHttpClient
    ): TextToSpeech = TtsManager(context, preferences, securePreferences, httpClient)

    @Provides
    @Singleton
    fun provideVoicePipeline(
        @ApplicationContext context: Context,
        stt: SpeechToText,
        tts: TextToSpeech,
        router: ConversationRouter,
        toolExecutor: ToolExecutor,
        moshi: Moshi,
        preferences: AppPreferences,
        sessionDao: SessionDao,
        messageDao: MessageDao,
        fastPathRouter: FastPathRouter,
        latencyRecorder: LatencyRecorder
    ): VoicePipeline = VoicePipeline(
        context = context,
        stt = stt,
        tts = tts,
        router = router,
        toolExecutor = toolExecutor,
        moshi = moshi,
        preferences = preferences,
        sessionDao = sessionDao,
        messageDao = messageDao,
        fastPathRouter = fastPathRouter,
        latencyRecorder = latencyRecorder
    )

    @Provides
    @Singleton
    fun provideLatencyRecorder(): LatencyRecorder = LatencyRecorder()
}
