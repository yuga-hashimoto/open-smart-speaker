package com.opendash.app.e2e.fakes

import com.opendash.app.di.TtsModule
import com.opendash.app.voice.tts.TextToSpeech
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

/**
 * Replaces [TtsModule] in instrumented tests so the production
 * `TtsManager` (which would talk to Android TTS / VOICEVOX / etc.) is
 * swapped for a recording [FakeTextToSpeech].
 *
 * Active for every `@HiltAndroidTest` in this APK because Hilt's test
 * runner loads the test entry points unconditionally.
 *
 * Tests inject `FakeTextToSpeech` directly via `@Inject lateinit var
 * fakeTts: FakeTextToSpeech` — the binding below registers the same
 * singleton against both [TextToSpeech] and the concrete fake type.
 */
@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [TtsModule::class]
)
object FakeTtsTestModule {

    @Provides
    @Singleton
    fun provideFakeTts(): FakeTextToSpeech = FakeTextToSpeech()

    @Provides
    @Singleton
    fun provideTextToSpeech(fake: FakeTextToSpeech): TextToSpeech = fake
}
