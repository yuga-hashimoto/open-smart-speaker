package com.opensmarthome.speaker.ui.settings.providers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opensmarthome.speaker.assistant.provider.AssistantProvider
import com.opensmarthome.speaker.assistant.router.ConversationRouter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Surfaces every registered AssistantProvider with its capabilities so the user
 * can tap to select one. Active provider sticks via ConversationRouter.selectProvider.
 */
@HiltViewModel
class ProvidersViewModel @Inject constructor(
    private val router: ConversationRouter
) : ViewModel() {

    data class Row(
        val id: String,
        val displayName: String,
        val modelName: String,
        val isLocal: Boolean,
        val supportsStreaming: Boolean,
        val supportsTools: Boolean,
        val supportsVision: Boolean,
        val isActive: Boolean
    )

    val rows: StateFlow<List<Row>> = combine(
        router.availableProviders,
        router.activeProvider
    ) { providers, active ->
        providers.map { p -> p.toRow(active) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun select(providerId: String) {
        viewModelScope.launch {
            router.selectProvider(providerId)
        }
    }

    private fun AssistantProvider.toRow(active: AssistantProvider?): Row = Row(
        id = id,
        displayName = displayName,
        modelName = capabilities.modelName,
        isLocal = capabilities.isLocal,
        supportsStreaming = capabilities.supportsStreaming,
        supportsTools = capabilities.supportsTools,
        supportsVision = capabilities.supportsVision,
        isActive = active?.id == id
    )
}
