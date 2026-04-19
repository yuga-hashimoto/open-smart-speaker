package com.opendash.app.ui.settings

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opendash.app.data.preferences.AppPreferences
import com.opendash.app.data.preferences.PreferenceKeys
import com.opendash.app.data.preferences.SecurePreferences
import com.opendash.app.voice.tts.AndroidTtsProvider
import com.opendash.app.voice.tts.TextToSpeech
import com.opendash.app.voice.tts.TtsUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: AppPreferences,
    private val securePreferences: SecurePreferences,
    private val tts: TextToSpeech,
    private val application: Application
) : ViewModel() {

    // Connection settings
    private val _haBaseUrl = MutableStateFlow("")
    val haBaseUrl: StateFlow<String> = _haBaseUrl.asStateFlow()

    private val _haToken = MutableStateFlow("")
    val haToken: StateFlow<String> = _haToken.asStateFlow()

    private val _openClawUrl = MutableStateFlow("")
    val openClawUrl: StateFlow<String> = _openClawUrl.asStateFlow()

    private val _localLlmUrl = MutableStateFlow("http://localhost:8080")
    val localLlmUrl: StateFlow<String> = _localLlmUrl.asStateFlow()

    private val _localLlmModel = MutableStateFlow("gemma-4-e2b")
    val localLlmModel: StateFlow<String> = _localLlmModel.asStateFlow()

    private val _switchBotToken = MutableStateFlow("")
    val switchBotToken: StateFlow<String> = _switchBotToken.asStateFlow()

    private val _switchBotSecret = MutableStateFlow("")
    val switchBotSecret: StateFlow<String> = _switchBotSecret.asStateFlow()

    private val _mqttBrokerUrl = MutableStateFlow("")
    val mqttBrokerUrl: StateFlow<String> = _mqttBrokerUrl.asStateFlow()

    // Wake word
    private val _wakeWord = MutableStateFlow("dash")
    val wakeWord: StateFlow<String> = _wakeWord.asStateFlow()

    private val _wakeWordSensitivity = MutableStateFlow(0.6f)
    val wakeWordSensitivity: StateFlow<Float> = _wakeWordSensitivity.asStateFlow()

    private val _batterySaverEnabled = MutableStateFlow(false)
    val batterySaverEnabled: StateFlow<Boolean> = _batterySaverEnabled.asStateFlow()

    private val _multiroomBroadcastEnabled = MutableStateFlow(false)
    val multiroomBroadcastEnabled: StateFlow<Boolean> = _multiroomBroadcastEnabled.asStateFlow()

    private val _multiroomSecret = MutableStateFlow("")
    val multiroomSecret: StateFlow<String> = _multiroomSecret.asStateFlow()

    // TTS settings
    private val _ttsSpeechRate = MutableStateFlow(1.0f)
    val ttsSpeechRate: StateFlow<Float> = _ttsSpeechRate.asStateFlow()

    private val _ttsPitch = MutableStateFlow(1.0f)
    val ttsPitch: StateFlow<Float> = _ttsPitch.asStateFlow()

    private val _ttsEngine = MutableStateFlow("")
    val ttsEngine: StateFlow<String> = _ttsEngine.asStateFlow()

    private val _ttsEnabled = MutableStateFlow(true)
    val ttsEnabled: StateFlow<Boolean> = _ttsEnabled.asStateFlow()

    private val _availableEngines = MutableStateFlow<List<TtsUtils.EngineInfo>>(emptyList())
    val availableEngines: StateFlow<List<TtsUtils.EngineInfo>> = _availableEngines.asStateFlow()

    // Voice interaction settings
    private val _continuousMode = MutableStateFlow(false)
    val continuousMode: StateFlow<Boolean> = _continuousMode.asStateFlow()

    private val _thinkingSound = MutableStateFlow(true)
    val thinkingSound: StateFlow<Boolean> = _thinkingSound.asStateFlow()

    private val _bargeInEnabled = MutableStateFlow(true)
    val bargeInEnabled: StateFlow<Boolean> = _bargeInEnabled.asStateFlow()

    private val _silenceTimeoutMs = MutableStateFlow(1500L)
    val silenceTimeoutMs: StateFlow<Long> = _silenceTimeoutMs.asStateFlow()

    private val _minSpeechMs = MutableStateFlow(400L)
    val minSpeechMs: StateFlow<Long> = _minSpeechMs.asStateFlow()

    private val _mediaButtonEnabled = MutableStateFlow(false)
    val mediaButtonEnabled: StateFlow<Boolean> = _mediaButtonEnabled.asStateFlow()

    // STT language
    private val _sttLanguage = MutableStateFlow("")
    val sttLanguage: StateFlow<String> = _sttLanguage.asStateFlow()

    // STT provider ("android" | "vosk" | "whisper")
    private val _sttProviderType = MutableStateFlow("android")
    val sttProviderType: StateFlow<String> = _sttProviderType.asStateFlow()

    // TTS language
    private val _ttsLanguage = MutableStateFlow("")
    val ttsLanguage: StateFlow<String> = _ttsLanguage.asStateFlow()

    // Hotword enabled
    private val _hotwordEnabled = MutableStateFlow(true)
    val hotwordEnabled: StateFlow<Boolean> = _hotwordEnabled.asStateFlow()

    // Filler phrases
    private val _fillerPhrasesEnabled = MutableStateFlow(false)
    val fillerPhrasesEnabled: StateFlow<Boolean> = _fillerPhrasesEnabled.asStateFlow()

    // Resume last session
    private val _resumeLastSession = MutableStateFlow(false)
    val resumeLastSession: StateFlow<Boolean> = _resumeLastSession.asStateFlow()

    // TTS provider
    private val _ttsProvider = MutableStateFlow("android")
    val ttsProvider: StateFlow<String> = _ttsProvider.asStateFlow()

    // OpenAI TTS
    private val _openAiTtsApiKey = MutableStateFlow("")
    val openAiTtsApiKey: StateFlow<String> = _openAiTtsApiKey.asStateFlow()
    private val _openAiTtsVoice = MutableStateFlow("alloy")
    val openAiTtsVoice: StateFlow<String> = _openAiTtsVoice.asStateFlow()
    private val _openAiTtsModel = MutableStateFlow("tts-1")
    val openAiTtsModel: StateFlow<String> = _openAiTtsModel.asStateFlow()

    // ElevenLabs
    private val _elevenLabsApiKey = MutableStateFlow("")
    val elevenLabsApiKey: StateFlow<String> = _elevenLabsApiKey.asStateFlow()
    private val _elevenLabsVoiceId = MutableStateFlow("21m00Tcm4TlvDq8ikWAM")
    val elevenLabsVoiceId: StateFlow<String> = _elevenLabsVoiceId.asStateFlow()
    private val _elevenLabsModel = MutableStateFlow("eleven_multilingual_v2")
    val elevenLabsModel: StateFlow<String> = _elevenLabsModel.asStateFlow()

    // VOICEVOX
    private val _voicevoxBaseUrl = MutableStateFlow("http://localhost:50021")
    val voicevoxBaseUrl: StateFlow<String> = _voicevoxBaseUrl.asStateFlow()
    private val _voicevoxSpeakerId = MutableStateFlow(3)
    val voicevoxSpeakerId: StateFlow<Int> = _voicevoxSpeakerId.asStateFlow()
    private val _voicevoxTermsAccepted = MutableStateFlow(false)
    val voicevoxTermsAccepted: StateFlow<Boolean> = _voicevoxTermsAccepted.asStateFlow()

    /**
     * User-chosen default location for the weather fast-path when the
     * utterance has no explicit place ("what's the weather?").
     */
    private val _defaultLocation = MutableStateFlow("")
    val defaultLocation: StateFlow<String> = _defaultLocation.asStateFlow()

    init {
        viewModelScope.launch { preferences.observe(PreferenceKeys.HA_BASE_URL).collect { _haBaseUrl.value = it ?: "" } }
        viewModelScope.launch { preferences.observe(PreferenceKeys.OPENCLAW_GATEWAY_URL).collect { _openClawUrl.value = it ?: "" } }
        viewModelScope.launch { preferences.observe(PreferenceKeys.LOCAL_LLM_BASE_URL).collect { _localLlmUrl.value = it ?: "http://localhost:8080" } }
        viewModelScope.launch { preferences.observe(PreferenceKeys.LOCAL_LLM_MODEL).collect { _localLlmModel.value = it ?: "gemma-4-e2b" } }
        // SwitchBot token is a credential — load from SecurePreferences, not plaintext DataStore
        _switchBotToken.value = securePreferences.getString(SecurePreferences.KEY_SWITCHBOT_TOKEN)
        _multiroomSecret.value = securePreferences.getString(SecurePreferences.KEY_MULTIROOM_SECRET)
        viewModelScope.launch { preferences.observe(PreferenceKeys.MQTT_BROKER_URL).collect { _mqttBrokerUrl.value = it ?: "" } }
        viewModelScope.launch { preferences.observe(PreferenceKeys.WAKE_WORD).collect { _wakeWord.value = it ?: "dash" } }
        viewModelScope.launch { preferences.observe(PreferenceKeys.WAKE_WORD_SENSITIVITY).collect { _wakeWordSensitivity.value = it ?: 0.6f } }
        viewModelScope.launch { preferences.observe(PreferenceKeys.BATTERY_SAVER_ENABLED).collect { _batterySaverEnabled.value = it ?: false } }
        viewModelScope.launch { preferences.observe(PreferenceKeys.MULTIROOM_BROADCAST_ENABLED).collect { _multiroomBroadcastEnabled.value = it ?: false } }
        viewModelScope.launch { preferences.observe(PreferenceKeys.TTS_SPEECH_RATE).collect { _ttsSpeechRate.value = it ?: 1.0f } }
        viewModelScope.launch { preferences.observe(PreferenceKeys.TTS_PITCH).collect { _ttsPitch.value = it ?: 1.0f } }
        viewModelScope.launch { preferences.observe(PreferenceKeys.TTS_ENGINE).collect { _ttsEngine.value = it ?: "" } }
        viewModelScope.launch { preferences.observe(PreferenceKeys.TTS_ENABLED).collect { _ttsEnabled.value = it ?: true } }
        viewModelScope.launch { preferences.observe(PreferenceKeys.CONTINUOUS_MODE).collect { _continuousMode.value = it ?: false } }
        viewModelScope.launch { preferences.observe(PreferenceKeys.THINKING_SOUND).collect { _thinkingSound.value = it ?: true } }
        viewModelScope.launch { preferences.observe(PreferenceKeys.BARGE_IN_ENABLED).collect { _bargeInEnabled.value = it ?: true } }
        viewModelScope.launch { preferences.observe(PreferenceKeys.SILENCE_TIMEOUT_MS).collect { _silenceTimeoutMs.value = it ?: 1500L } }
        viewModelScope.launch { preferences.observe(PreferenceKeys.MIN_SPEECH_MS).collect { _minSpeechMs.value = it ?: 400L } }
        viewModelScope.launch { preferences.observe(PreferenceKeys.MEDIA_BUTTON_ENABLED).collect { _mediaButtonEnabled.value = it ?: false } }
        viewModelScope.launch { preferences.observe(PreferenceKeys.STT_LANGUAGE).collect { _sttLanguage.value = it ?: "" } }
        viewModelScope.launch { preferences.observe(PreferenceKeys.STT_PROVIDER_TYPE).collect { _sttProviderType.value = it ?: "android" } }
        viewModelScope.launch { preferences.observe(PreferenceKeys.TTS_LANGUAGE).collect { _ttsLanguage.value = it ?: "" } }
        viewModelScope.launch { preferences.observe(PreferenceKeys.HOTWORD_ENABLED).collect { _hotwordEnabled.value = it ?: true } }
        viewModelScope.launch { preferences.observe(PreferenceKeys.FILLER_PHRASES_ENABLED).collect { _fillerPhrasesEnabled.value = it ?: false } }
        viewModelScope.launch { preferences.observe(PreferenceKeys.RESUME_LAST_SESSION).collect { _resumeLastSession.value = it ?: false } }
        viewModelScope.launch { preferences.observe(PreferenceKeys.TTS_PROVIDER).collect { _ttsProvider.value = it ?: "android" } }
        viewModelScope.launch { preferences.observe(PreferenceKeys.OPENAI_TTS_VOICE).collect { _openAiTtsVoice.value = it ?: "alloy" } }
        viewModelScope.launch { preferences.observe(PreferenceKeys.OPENAI_TTS_MODEL).collect { _openAiTtsModel.value = it ?: "tts-1" } }
        viewModelScope.launch { preferences.observe(PreferenceKeys.ELEVENLABS_VOICE_ID).collect { _elevenLabsVoiceId.value = it ?: "21m00Tcm4TlvDq8ikWAM" } }
        viewModelScope.launch { preferences.observe(PreferenceKeys.ELEVENLABS_MODEL).collect { _elevenLabsModel.value = it ?: "eleven_multilingual_v2" } }
        viewModelScope.launch { preferences.observe(PreferenceKeys.VOICEVOX_BASE_URL).collect { _voicevoxBaseUrl.value = it ?: "http://localhost:50021" } }
        viewModelScope.launch { preferences.observe(PreferenceKeys.VOICEVOX_STYLE_ID).collect { _voicevoxSpeakerId.value = it ?: 3 } }
        viewModelScope.launch { preferences.observe(PreferenceKeys.VOICEVOX_TERMS_ACCEPTED).collect { _voicevoxTermsAccepted.value = it ?: false } }
        viewModelScope.launch { preferences.observe(PreferenceKeys.DEFAULT_LOCATION).collect { _defaultLocation.value = it ?: "" } }
        _openAiTtsApiKey.value = securePreferences.getString(SecurePreferences.KEY_OPENAI_TTS_API_KEY)
        _elevenLabsApiKey.value = securePreferences.getString(SecurePreferences.KEY_ELEVENLABS_API_KEY)

        _haToken.value = securePreferences.getString(SecurePreferences.KEY_HA_TOKEN)
        _switchBotSecret.value = securePreferences.getString(SecurePreferences.KEY_SWITCHBOT_SECRET)
        _availableEngines.value = TtsUtils.getAvailableEngines(application)
    }

    // Connection settings
    fun saveHaSettings(baseUrl: String, token: String) {
        viewModelScope.launch {
            preferences.set(PreferenceKeys.HA_BASE_URL, baseUrl)
            securePreferences.putString(SecurePreferences.KEY_HA_TOKEN, token)
            _haToken.value = token
        }
    }

    fun saveOpenClawSettings(url: String) {
        viewModelScope.launch { preferences.set(PreferenceKeys.OPENCLAW_GATEWAY_URL, url) }
    }

    fun saveLocalLlmSettings(url: String, model: String) {
        viewModelScope.launch {
            preferences.set(PreferenceKeys.LOCAL_LLM_BASE_URL, url)
            preferences.set(PreferenceKeys.LOCAL_LLM_MODEL, model)
        }
    }

    fun saveSwitchBotSettings(token: String, secret: String) {
        viewModelScope.launch {
            securePreferences.putString(SecurePreferences.KEY_SWITCHBOT_TOKEN, token)
            securePreferences.putString(SecurePreferences.KEY_SWITCHBOT_SECRET, secret)
            _switchBotToken.value = token
            _switchBotSecret.value = secret
        }
    }

    fun saveMqttSettings(brokerUrl: String) {
        viewModelScope.launch { preferences.set(PreferenceKeys.MQTT_BROKER_URL, brokerUrl) }
    }

    fun saveWakeWord(word: String) {
        viewModelScope.launch { preferences.set(PreferenceKeys.WAKE_WORD, word.lowercase().trim()) }
    }

    fun saveWakeWordSensitivity(sensitivity: Float) {
        val clamped = sensitivity.coerceIn(0f, 1f)
        viewModelScope.launch { preferences.set(PreferenceKeys.WAKE_WORD_SENSITIVITY, clamped) }
    }

    fun saveBatterySaverEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.set(PreferenceKeys.BATTERY_SAVER_ENABLED, enabled) }
    }

    fun saveMultiroomBroadcastEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.set(PreferenceKeys.MULTIROOM_BROADCAST_ENABLED, enabled) }
    }

    fun saveMultiroomSecret(secret: String) {
        val trimmed = secret.trim()
        securePreferences.putString(SecurePreferences.KEY_MULTIROOM_SECRET, trimmed)
        _multiroomSecret.value = trimmed
    }

    fun saveSttProviderType(type: String) {
        viewModelScope.launch { preferences.set(PreferenceKeys.STT_PROVIDER_TYPE, type) }
    }

    // TTS settings
    fun saveTtsSpeechRate(rate: Float) {
        viewModelScope.launch {
            preferences.set(PreferenceKeys.TTS_SPEECH_RATE, rate)
            (tts as? com.opendash.app.voice.tts.TtsManager)?.setSpeechRate(rate)
        }
    }

    fun saveTtsPitch(pitch: Float) {
        viewModelScope.launch {
            preferences.set(PreferenceKeys.TTS_PITCH, pitch)
            (tts as? com.opendash.app.voice.tts.TtsManager)?.setPitch(pitch)
        }
    }

    fun saveTtsEngine(engine: String) {
        viewModelScope.launch {
            preferences.set(PreferenceKeys.TTS_ENGINE, engine)
            (tts as? com.opendash.app.voice.tts.TtsManager)?.reinitialize(engine)
        }
    }

    fun saveTtsEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.set(PreferenceKeys.TTS_ENABLED, enabled) }
    }

    // Voice interaction settings
    fun saveContinuousMode(enabled: Boolean) {
        viewModelScope.launch { preferences.set(PreferenceKeys.CONTINUOUS_MODE, enabled) }
    }

    fun saveThinkingSound(enabled: Boolean) {
        viewModelScope.launch { preferences.set(PreferenceKeys.THINKING_SOUND, enabled) }
    }

    fun saveBargeInEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.set(PreferenceKeys.BARGE_IN_ENABLED, enabled) }
    }

    fun saveSilenceTimeout(ms: Long) {
        viewModelScope.launch { preferences.set(PreferenceKeys.SILENCE_TIMEOUT_MS, ms) }
    }

    fun saveMinSpeechMs(ms: Long) {
        viewModelScope.launch { preferences.set(PreferenceKeys.MIN_SPEECH_MS, ms) }
    }

    fun saveMediaButtonEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.set(PreferenceKeys.MEDIA_BUTTON_ENABLED, enabled) }
    }

    // STT language
    fun saveSttLanguage(lang: String) {
        viewModelScope.launch { preferences.set(PreferenceKeys.STT_LANGUAGE, lang) }
    }

    // TTS language
    fun saveTtsLanguage(lang: String) {
        viewModelScope.launch {
            preferences.set(PreferenceKeys.TTS_LANGUAGE, lang)
            if (lang.isNotBlank()) {
                (tts as? com.opendash.app.voice.tts.TtsManager)?.setLanguage(lang)
            }
        }
    }

    // Hotword enabled
    fun saveHotwordEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.set(PreferenceKeys.HOTWORD_ENABLED, enabled) }
    }

    // Filler phrases
    fun saveFillerPhrasesEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.set(PreferenceKeys.FILLER_PHRASES_ENABLED, enabled) }
    }

    // Resume last session
    fun saveResumeLastSession(enabled: Boolean) {
        viewModelScope.launch { preferences.set(PreferenceKeys.RESUME_LAST_SESSION, enabled) }
    }

    // TTS Provider
    fun saveTtsProvider(provider: String) {
        viewModelScope.launch { preferences.set(PreferenceKeys.TTS_PROVIDER, provider) }
    }

    fun saveOpenAiTts(apiKey: String, voice: String, model: String) {
        viewModelScope.launch {
            securePreferences.putString(SecurePreferences.KEY_OPENAI_TTS_API_KEY, apiKey)
            preferences.set(PreferenceKeys.OPENAI_TTS_VOICE, voice)
            preferences.set(PreferenceKeys.OPENAI_TTS_MODEL, model)
            _openAiTtsApiKey.value = apiKey
        }
    }

    fun saveElevenLabs(apiKey: String, voiceId: String, model: String) {
        viewModelScope.launch {
            securePreferences.putString(SecurePreferences.KEY_ELEVENLABS_API_KEY, apiKey)
            preferences.set(PreferenceKeys.ELEVENLABS_VOICE_ID, voiceId)
            preferences.set(PreferenceKeys.ELEVENLABS_MODEL, model)
            _elevenLabsApiKey.value = apiKey
        }
    }

    fun saveVoiceVox(baseUrl: String, speakerId: Int, termsAccepted: Boolean) {
        viewModelScope.launch {
            preferences.set(PreferenceKeys.VOICEVOX_BASE_URL, baseUrl)
            preferences.set(PreferenceKeys.VOICEVOX_STYLE_ID, speakerId)
            preferences.set(PreferenceKeys.VOICEVOX_TERMS_ACCEPTED, termsAccepted)
        }
    }

    /**
     * Persist the user's default weather location. Trimmed; an empty string
     * means "no default, fall back to the provider's built-in default".
     */
    fun saveDefaultLocation(value: String) {
        viewModelScope.launch { preferences.set(PreferenceKeys.DEFAULT_LOCATION, value.trim()) }
    }
}
