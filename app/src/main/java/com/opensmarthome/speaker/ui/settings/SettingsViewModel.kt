package com.opensmarthome.speaker.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opensmarthome.speaker.data.preferences.AppPreferences
import com.opensmarthome.speaker.data.preferences.PreferenceKeys
import com.opensmarthome.speaker.data.preferences.SecurePreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: AppPreferences,
    private val securePreferences: SecurePreferences
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
        // Load encrypted token
        _haToken.value = securePreferences.getString(SecurePreferences.KEY_HA_TOKEN)
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
}
