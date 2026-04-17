package com.opensmarthome.speaker.voice.tts

import android.content.Context
import com.google.common.truth.Truth.assertThat
import com.opensmarthome.speaker.data.preferences.AppPreferences
import com.opensmarthome.speaker.data.preferences.PreferenceKeys
import com.opensmarthome.speaker.data.preferences.SecurePreferences
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import org.junit.jupiter.api.Test

// ---------------------------------------------------------------------------
// Fakes
// ---------------------------------------------------------------------------

/**
 * Recording stand-in for [AndroidTtsProvider].
 * [initialize] is a no-op so the class never touches [android.speech.tts.TextToSpeech],
 * keeping these tests runnable on a pure JVM.
 */
private class FakeAndroidTtsProvider : AndroidTtsProvider(mockk(relaxed = true)) {
    var appliedSpeechRate: Float? = null
    var appliedPitch: Float? = null
    var reinitializedEngine: String? = null

    override fun initialize(engine: String?) {
        // no-op — prevents android.speech.tts.TextToSpeech instantiation in unit tests
    }

    override fun setSpeechRate(rate: Float) {
        appliedSpeechRate = rate
    }

    override fun setPitch(value: Float) {
        appliedPitch = value
    }

    override fun reinitialize(engine: String?) {
        reinitializedEngine = engine
    }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

private fun buildPreferences(
    rate: Float? = null,
    pitch: Float? = null,
    engine: String? = null
): AppPreferences = mockk<AppPreferences>(relaxed = true).also { prefs ->
    every { prefs.observe(PreferenceKeys.TTS_SPEECH_RATE) } returns MutableStateFlow(rate)
    every { prefs.observe(PreferenceKeys.TTS_PITCH) } returns MutableStateFlow(pitch)
    every { prefs.observe(PreferenceKeys.TTS_ENGINE) } returns MutableStateFlow(engine)
    every { prefs.observe(PreferenceKeys.TTS_PROVIDER) } returns MutableStateFlow(null)
}

private fun buildManager(
    prefs: AppPreferences,
    fakeProvider: FakeAndroidTtsProvider
): TtsManager = TtsManager(
    context = mockk(relaxed = true),
    preferences = prefs,
    securePreferences = mockk(relaxed = true),
    httpClient = mockk(relaxed = true),
    androidTtsProvider = fakeProvider
)

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

class TtsManagerTest {

    @Test
    fun `persisted speech rate is applied to AndroidTtsProvider on startup`() {
        val fakeProvider = FakeAndroidTtsProvider()
        buildManager(buildPreferences(rate = 1.5f), fakeProvider)

        assertThat(fakeProvider.appliedSpeechRate).isEqualTo(1.5f)
    }

    @Test
    fun `persisted pitch is applied to AndroidTtsProvider on startup`() {
        val fakeProvider = FakeAndroidTtsProvider()
        buildManager(buildPreferences(pitch = 0.8f), fakeProvider)

        assertThat(fakeProvider.appliedPitch).isEqualTo(0.8f)
    }

    @Test
    fun `persisted engine is applied via reinitialize on startup`() {
        val fakeProvider = FakeAndroidTtsProvider()
        buildManager(buildPreferences(engine = "com.google.android.tts"), fakeProvider)

        assertThat(fakeProvider.reinitializedEngine).isEqualTo("com.google.android.tts")
    }

    @Test
    fun `no-op when speech rate is not persisted`() {
        val fakeProvider = FakeAndroidTtsProvider()
        buildManager(buildPreferences(rate = null), fakeProvider)

        assertThat(fakeProvider.appliedSpeechRate).isNull()
    }

    @Test
    fun `no-op when pitch is not persisted`() {
        val fakeProvider = FakeAndroidTtsProvider()
        buildManager(buildPreferences(pitch = null), fakeProvider)

        assertThat(fakeProvider.appliedPitch).isNull()
    }

    @Test
    fun `no-op when engine is not persisted`() {
        val fakeProvider = FakeAndroidTtsProvider()
        buildManager(buildPreferences(engine = null), fakeProvider)

        assertThat(fakeProvider.reinitializedEngine).isNull()
    }

    @Test
    fun `blank engine string is not applied`() {
        val fakeProvider = FakeAndroidTtsProvider()
        buildManager(buildPreferences(engine = "   "), fakeProvider)

        assertThat(fakeProvider.reinitializedEngine).isNull()
    }

    @Test
    fun `all three settings applied together`() {
        val fakeProvider = FakeAndroidTtsProvider()
        buildManager(
            buildPreferences(
                rate = 1.25f,
                pitch = 0.9f,
                engine = "com.samsung.SMT"
            ),
            fakeProvider
        )

        assertThat(fakeProvider.appliedSpeechRate).isEqualTo(1.25f)
        assertThat(fakeProvider.appliedPitch).isEqualTo(0.9f)
        assertThat(fakeProvider.reinitializedEngine).isEqualTo("com.samsung.SMT")
    }
}
