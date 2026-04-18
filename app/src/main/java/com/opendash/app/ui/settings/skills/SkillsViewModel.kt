package com.opendash.app.ui.settings.skills

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opendash.app.R
import com.opendash.app.assistant.skills.SkillInstaller
import com.opendash.app.assistant.skills.SkillRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class SkillsViewModel @Inject constructor(
    private val repository: SkillRepository,
    private val installer: SkillInstaller
) : ViewModel() {

    data class UiMessage(
        @StringRes val resId: Int,
        val args: List<String> = emptyList(),
    )

    sealed class UiState {
        data object Loading : UiState()
        data class Loaded(
            val skills: List<SkillRepository.SkillView>,
            val installing: Boolean = false,
            val errorMessage: UiMessage? = null
        ) : UiState()
    }

    private val _state = MutableStateFlow<UiState>(UiState.Loading)
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _state.value = UiState.Loaded(skills = repository.listAll())
    }

    fun delete(name: String) {
        viewModelScope.launch {
            val ok = repository.delete(name)
            if (!ok) {
                setError(UiMessage(R.string.skills_error_bundled_or_removed, listOf(name)))
            }
            refresh()
        }
    }

    fun setEnabled(name: String, enabled: Boolean) {
        repository.setEnabled(name, enabled)
        refresh()
    }

    fun reloadFromDisk() {
        viewModelScope.launch {
            repository.reloadFromDisk()
            refresh()
        }
    }

    fun installFromUrl(url: String) {
        val trimmed = url.trim()
        if (trimmed.isBlank()) {
            setError(UiMessage(R.string.skills_error_url_empty))
            return
        }
        setInstalling(true)
        viewModelScope.launch {
            val result = try {
                installer.installFromUrl(trimmed)
            } catch (e: Exception) {
                Timber.w(e, "Install failed")
                SkillInstaller.Result.Failed(e.message ?: "Install failed")
            }
            when (result) {
                is SkillInstaller.Result.Installed -> refresh()
                is SkillInstaller.Result.Failed ->
                    setError(UiMessage(R.string.skills_error_install_failed, listOf(result.reason)))
            }
            setInstalling(false)
        }
    }

    fun clearError() {
        (_state.value as? UiState.Loaded)?.let {
            _state.value = it.copy(errorMessage = null)
        }
    }

    private fun setError(message: UiMessage) {
        val current = _state.value as? UiState.Loaded ?: UiState.Loaded(emptyList())
        _state.value = current.copy(errorMessage = message)
    }

    private fun setInstalling(installing: Boolean) {
        val current = _state.value as? UiState.Loaded ?: UiState.Loaded(emptyList())
        _state.value = current.copy(installing = installing)
    }
}
