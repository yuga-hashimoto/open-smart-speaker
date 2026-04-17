package com.opensmarthome.speaker.voice.stt

import com.google.common.truth.Truth.assertThat
import com.opensmarthome.speaker.data.preferences.AppPreferences
import com.opensmarthome.speaker.data.preferences.PreferenceKeys
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

private class RecordingStt(private val label: String) : SpeechToText {
    private val _listening = MutableStateFlow(false)
    override val isListening: StateFlow<Boolean> = _listening.asStateFlow()
    var startCount = 0
    override fun startListening() = flowOf<SttResult>(SttResult.Final(label, 1.0f)).also {
        startCount++
        _listening.value = true
    }
    override fun stopListening() { _listening.value = false }
}

class DelegatingSttProviderTest {

    private fun fakePrefs(value: String?): AppPreferences {
        val prefs = mockk<AppPreferences>()
        every { prefs.observe(PreferenceKeys.STT_PROVIDER_TYPE) } returns flowOf(value)
        return prefs
    }

    @Test
    fun `default type routes to android backend`() = runTest {
        val android = RecordingStt("android")
        val vosk = RecordingStt("vosk")
        val whisper = RecordingStt("whisper")
        val d = DelegatingSttProvider(fakePrefs(null), android, vosk, whisper)

        val results = d.startListening().toList()

        assertThat((results.first() as SttResult.Final).text).isEqualTo("android")
        assertThat(android.startCount).isEqualTo(1)
        assertThat(vosk.startCount).isEqualTo(0)
        assertThat(whisper.startCount).isEqualTo(0)
    }

    @Test
    fun `vosk preference routes to vosk backend`() = runTest {
        val android = RecordingStt("android")
        val vosk = RecordingStt("vosk")
        val whisper = RecordingStt("whisper")
        val d = DelegatingSttProvider(fakePrefs("vosk"), android, vosk, whisper)

        d.startListening().toList()

        assertThat(android.startCount).isEqualTo(0)
        assertThat(vosk.startCount).isEqualTo(1)
        assertThat(whisper.startCount).isEqualTo(0)
    }

    @Test
    fun `whisper preference routes to whisper backend`() = runTest {
        val android = RecordingStt("android")
        val vosk = RecordingStt("vosk")
        val whisper = RecordingStt("whisper")
        val d = DelegatingSttProvider(fakePrefs("whisper"), android, vosk, whisper)

        d.startListening().toList()

        assertThat(whisper.startCount).isEqualTo(1)
    }

    @Test
    fun `unknown preference value falls back to android`() = runTest {
        val android = RecordingStt("android")
        val d = DelegatingSttProvider(fakePrefs("nonsense"), android)

        d.startListening().toList()

        assertThat(android.startCount).isEqualTo(1)
    }

    @Test
    fun `offline stub emits error result to surface coming-soon message`() = runTest {
        val stub = OfflineSttStub("Whisper")
        val emitted = stub.startListening().toList()

        assertThat(emitted).hasSize(1)
        assertThat(emitted[0]).isInstanceOf(SttResult.Error::class.java)
        assertThat((emitted[0] as SttResult.Error).message).contains("Whisper")
    }

    @Test
    fun `SttProviderType fromPref is case-insensitive and defaults to ANDROID`() {
        assertThat(SttProviderType.fromPref("VOSK")).isEqualTo(SttProviderType.VOSK_OFFLINE)
        assertThat(SttProviderType.fromPref("Whisper")).isEqualTo(SttProviderType.WHISPER_OFFLINE)
        assertThat(SttProviderType.fromPref(null)).isEqualTo(SttProviderType.ANDROID)
        assertThat(SttProviderType.fromPref("")).isEqualTo(SttProviderType.ANDROID)
        assertThat(SttProviderType.fromPref("bogus")).isEqualTo(SttProviderType.ANDROID)
    }
}
