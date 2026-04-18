package com.opendash.app.ui.settings.prompt

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opendash.app.assistant.provider.embedded.EmbeddedLlmConfig
import com.opendash.app.data.preferences.AppPreferences
import com.opendash.app.data.preferences.PreferenceKeys
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SystemPromptViewModel @Inject constructor(
    private val preferences: AppPreferences
) : ViewModel() {

    data class UiState(
        val prompt: String = "",
        val defaultPrompt: String = EmbeddedLlmConfig.DEFAULT_SYSTEM_PROMPT,
        val saved: Boolean = false,
        val usingDefault: Boolean = true
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val custom = preferences.observe(PreferenceKeys.CUSTOM_SYSTEM_PROMPT).first()
            _state.value = UiState(
                prompt = custom ?: EmbeddedLlmConfig.DEFAULT_SYSTEM_PROMPT,
                usingDefault = custom.isNullOrBlank()
            )
        }
    }

    fun updatePrompt(text: String) {
        _state.value = _state.value.copy(prompt = text, saved = false)
    }

    fun save() {
        viewModelScope.launch {
            val current = _state.value.prompt.trim()
            if (current.isBlank() || current == EmbeddedLlmConfig.DEFAULT_SYSTEM_PROMPT) {
                preferences.set(PreferenceKeys.CUSTOM_SYSTEM_PROMPT, "")
                _state.value = _state.value.copy(saved = true, usingDefault = true)
            } else {
                preferences.set(PreferenceKeys.CUSTOM_SYSTEM_PROMPT, current)
                _state.value = _state.value.copy(saved = true, usingDefault = false)
            }
        }
    }

    fun resetToDefault() {
        viewModelScope.launch {
            preferences.set(PreferenceKeys.CUSTOM_SYSTEM_PROMPT, "")
            _state.value = UiState(
                prompt = EmbeddedLlmConfig.DEFAULT_SYSTEM_PROMPT,
                saved = true,
                usingDefault = true
            )
        }
    }
}
