package com.opendash.app.ui.settings.documents

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opendash.app.R
import com.opendash.app.tool.rag.RagRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DocumentsViewModel @Inject constructor(
    private val repository: RagRepository
) : ViewModel() {

    data class UiMessage(
        @StringRes val resId: Int,
        val args: List<String> = emptyList(),
    )

    data class UiState(
        val documents: List<RagRepository.DocumentSummary> = emptyList(),
        val loading: Boolean = true,
        val ingesting: Boolean = false,
        val message: UiMessage? = null
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val docs = repository.listDocuments()
            _state.value = _state.value.copy(documents = docs, loading = false)
        }
    }

    fun ingest(title: String, content: String) {
        val trimmedTitle = title.trim()
        val trimmedContent = content.trim()
        if (trimmedTitle.isBlank() || trimmedContent.isBlank()) {
            _state.value = _state.value.copy(
                message = UiMessage(R.string.documents_error_title_content_required)
            )
            return
        }
        _state.value = _state.value.copy(ingesting = true)
        viewModelScope.launch {
            runCatching { repository.ingest(trimmedTitle, trimmedContent) }
                .onSuccess {
                    _state.value = _state.value.copy(
                        ingesting = false,
                        message = UiMessage(R.string.documents_added_message, listOf(trimmedTitle))
                    )
                    refresh()
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        ingesting = false,
                        message = UiMessage(
                            R.string.documents_error_ingest_failed,
                            listOf(e.message.orEmpty())
                        )
                    )
                }
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            repository.delete(id)
            refresh()
        }
    }

    fun clearMessage() {
        _state.value = _state.value.copy(message = null)
    }
}
