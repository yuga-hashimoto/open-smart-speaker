package com.opensmarthome.speaker.di

import android.content.Context
import com.opensmarthome.speaker.assistant.router.ConversationRouter
import com.opensmarthome.speaker.tool.ToolExecutor
import com.opensmarthome.speaker.voice.pipeline.VoicePipeline
import com.opensmarthome.speaker.voice.stt.AndroidSttProvider
import com.opensmarthome.speaker.voice.stt.SpeechToText
import com.opensmarthome.speaker.voice.tts.AndroidTtsProvider
import com.opensmarthome.speaker.voice.tts.TextToSpeech
import com.squareup.moshi.Moshi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
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
    fun provideTextToSpeech(@ApplicationContext context: Context): TextToSpeech =
        AndroidTtsProvider(context)

    @Provides
    @Singleton
    fun provideVoicePipeline(
        stt: SpeechToText,
        tts: TextToSpeech,
        router: ConversationRouter,
        toolExecutor: ToolExecutor,
        moshi: Moshi
    ): VoicePipeline = VoicePipeline(stt, tts, router, toolExecutor, moshi)
}
