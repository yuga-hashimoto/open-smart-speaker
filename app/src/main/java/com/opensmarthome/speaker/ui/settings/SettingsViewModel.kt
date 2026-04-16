package com.opensmarthome.speaker.ui.settings

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opensmarthome.speaker.data.preferences.AppPreferences
import com.opensmarthome.speaker.data.preferences.PreferenceKeys
import com.opensmarthome.speaker.data.preferences.SecurePreferences
import com.opensmarthome.speaker.voice.tts.AndroidTtsProvider
import com.opensmarthome.speaker.voice.tts.TextToSpeech
import com.opensmarthome.speaker.voice.tts.TtsUtils
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

    private val _wakeWord = MutableStateFlow("hey speaker")
    val wakeWord: StateFlow<String> = _wakeWord.asStateFlow()

    // TTS settings
    private val _ttsSpeechRate = MutableStateFlow(1.0f)
    val ttsSpeechRate: StateFlow<Float> = _ttsSpeechRate.asStateFlow()

    private val _ttsPitch = MutableStateFlow(1.0f)
    val ttsPitch: StateFlow<Float> = _ttsPitch.asStateFlow()

    private val _ttsEngine = MutableStateFlow("")
    val ttsEngine: StateFlow<String> = _ttsEngine.asStateFlow()

    private val _availableEngines = MutableStateFlow<List<TtsUtils.EngineInfo>>(emptyList())
    val availableEngines: StateFlow<List<TtsUtils.EngineInfo>> = _availableEngines.asStateFlow()

    init {
        viewModelScope.launch {
            preferences.observe(PreferenceKeys.HA_BASE_URL).collect { _haBaseUrl.value = it ?: "" }
        }
        viewModelScope.launch {
            preferences.observe(PreferenceKeys.OPENCLAW_GATEWAY_URL).collect { _openClawUrl.value = it ?: "" }
        }
        viewModelScope.launch {
            preferences.observe(PreferenceKeys.LOCAL_LLM_BASE_URL).collect { _localLlmUrl.value = it ?: "http://localhost:8080" }
        }
        viewModelScope.launch {
            preferences.observe(PreferenceKeys.LOCAL_LLM_MODEL).collect { _localLlmModel.value = it ?: "gemma-4-e2b" }
        }
        viewModelScope.launch {
            preferences.observe(PreferenceKeys.SWITCHBOT_TOKEN).collect { _switchBotToken.value = it ?: "" }
        }
        viewModelScope.launch {
            preferences.observe(PreferenceKeys.MQTT_BROKER_URL).collect { _mqttBrokerUrl.value = it ?: "" }
        }
        viewModelScope.launch {
            preferences.observe(PreferenceKeys.WAKE_WORD).collect { _wakeWord.value = it ?: "hey speaker" }
        }
        viewModelScope.launch {
            preferences.observe(PreferenceKeys.TTS_SPEECH_RATE).collect { _ttsSpeechRate.value = it ?: 1.0f }
        }
        viewModelScope.launch {
            preferences.observe(PreferenceKeys.TTS_PITCH).collect { _ttsPitch.value = it ?: 1.0f }
        }
        viewModelScope.launch {
            preferences.observe(PreferenceKeys.TTS_ENGINE).collect { _ttsEngine.value = it ?: "" }
        }
        _haToken.value = securePreferences.getString(SecurePreferences.KEY_HA_TOKEN)
        _switchBotSecret.value = securePreferences.getString("switchbot_secret")
        _availableEngines.value = TtsUtils.getAvailableEngines(application)
    }

    fun saveHaSettings(baseUrl: String, token: String) {
        viewModelScope.launch {
            preferences.set(PreferenceKeys.HA_BASE_URL, baseUrl)
            securePreferences.putString(SecurePreferences.KEY_HA_TOKEN, token)
            _haToken.value = token
        }
    }

    fun saveOpenClawSettings(url: String) {
        viewModelScope.launch {
            preferences.set(PreferenceKeys.OPENCLAW_GATEWAY_URL, url)
        }
    }

    fun saveLocalLlmSettings(url: String, model: String) {
        viewModelScope.launch {
            preferences.set(PreferenceKeys.LOCAL_LLM_BASE_URL, url)
            preferences.set(PreferenceKeys.LOCAL_LLM_MODEL, model)
        }
    }

    fun saveSwitchBotSettings(token: String, secret: String) {
        viewModelScope.launch {
            preferences.set(PreferenceKeys.SWITCHBOT_TOKEN, token)
            securePreferences.putString("switchbot_secret", secret)
            _switchBotToken.value = token
            _switchBotSecret.value = secret
        }
    }

    fun saveMqttSettings(brokerUrl: String) {
        viewModelScope.launch {
            preferences.set(PreferenceKeys.MQTT_BROKER_URL, brokerUrl)
        }
    }

    fun saveWakeWord(word: String) {
        viewModelScope.launch {
            preferences.set(PreferenceKeys.WAKE_WORD, word.lowercase().trim())
        }
    }

    fun saveTtsSpeechRate(rate: Float) {
        viewModelScope.launch {
            preferences.set(PreferenceKeys.TTS_SPEECH_RATE, rate)
            (tts as? AndroidTtsProvider)?.setSpeechRate(rate)
        }
    }

    fun saveTtsPitch(pitch: Float) {
        viewModelScope.launch {
            preferences.set(PreferenceKeys.TTS_PITCH, pitch)
            (tts as? AndroidTtsProvider)?.setPitch(pitch)
        }
    }

    fun saveTtsEngine(engine: String) {
        viewModelScope.launch {
            preferences.set(PreferenceKeys.TTS_ENGINE, engine)
            (tts as? AndroidTtsProvider)?.reinitialize(engine)
        }
    }
}
