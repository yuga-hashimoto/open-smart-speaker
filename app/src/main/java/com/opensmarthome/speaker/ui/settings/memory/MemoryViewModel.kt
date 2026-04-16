package com.opensmarthome.speaker.ui.settings.memory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opensmarthome.speaker.data.db.MemoryEntity
import com.opensmarthome.speaker.tool.memory.MemoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MemoryViewModel @Inject constructor(
    private val repository: MemoryRepository
) : ViewModel() {

    data class UiState(
        val entries: List<MemoryEntity> = emptyList(),
        val query: String = "",
        val loading: Boolean = true
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val entries = if (_state.value.query.isBlank()) {
                repository.all()
            } else {
                repository.search(_state.value.query)
            }
            _state.value = _state.value.copy(entries = entries, loading = false)
        }
    }

    fun updateQuery(query: String) {
        _state.value = _state.value.copy(query = query)
        refresh()
    }

    fun delete(key: String) {
        viewModelScope.launch {
            repository.delete(key)
            refresh()
        }
    }

    fun save(key: String, value: String) {
        viewModelScope.launch {
            if (key.isBlank()) return@launch
            repository.save(key, value)
            refresh()
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            repository.clear()
            refresh()
        }
    }
}
