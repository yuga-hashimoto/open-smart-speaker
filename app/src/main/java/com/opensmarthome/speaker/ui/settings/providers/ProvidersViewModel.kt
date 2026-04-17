package com.opensmarthome.speaker.ui.settings.providers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opensmarthome.speaker.assistant.provider.AssistantProvider
import com.opensmarthome.speaker.assistant.router.ConversationRouter
import com.opensmarthome.speaker.data.preferences.AppPreferences
import com.opensmarthome.speaker.data.preferences.PreferenceKeys
import com.opensmarthome.speaker.data.preferences.SecurePreferences
import com.opensmarthome.speaker.util.MulticastDiscovery
import com.opensmarthome.speaker.util.PeerLivenessTracker
import com.opensmarthome.speaker.util.Staleness
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
 *
 * Also exposes [multiroomState] — a snapshot of whether the multi-room mesh is
 * actually ready to work (broadcast on, secret set, peers reachable). The UI
 * renders this as a "Multi-room" card below the provider list so the user can
 * tell at a glance whether timer announcements and room-to-room handoff will
 * function, without drilling into diagnostics.
 */
@HiltViewModel
class ProvidersViewModel @Inject constructor(
    private val router: ConversationRouter,
    private val appPreferences: AppPreferences,
    private val securePreferences: SecurePreferences,
    private val discovery: MulticastDiscovery,
    private val liveness: PeerLivenessTracker
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

    /**
     * Snapshot of multi-room mesh health. All counts are derived from the
     * liveness tracker so the "peers" count matches what the mesh is actually
     * tracking, not just what mDNS found.
     */
    data class MultiroomState(
        val broadcastEnabled: Boolean,
        val hasSecret: Boolean,
        val broadcastingAs: String?,
        val peerCount: Int,
        val freshCount: Int,
        val staleCount: Int,
        val goneCount: Int
    ) {
        companion object {
            val Empty = MultiroomState(
                broadcastEnabled = false,
                hasSecret = false,
                broadcastingAs = null,
                peerCount = 0,
                freshCount = 0,
                staleCount = 0,
                goneCount = 0
            )
        }
    }

    val rows: StateFlow<List<Row>> = combine(
        router.availableProviders,
        router.activeProvider
    ) { providers, active ->
        providers.map { p -> p.toRow(active) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val multiroomState: StateFlow<MultiroomState> = combine(
        appPreferences.observe(PreferenceKeys.MULTIROOM_BROADCAST_ENABLED),
        discovery.registeredName,
        discovery.speakers,
        liveness.staleness
    ) { broadcastEnabled, registeredName, speakers, staleness ->
        // SecurePreferences read is synchronous, so we sample it inside the
        // combine — the card naturally re-renders when any of the other flows
        // tick, which is enough for this screen's "refresh on focus" semantics.
        val hasSecret = securePreferences
            .getString(SecurePreferences.KEY_MULTIROOM_SECRET)
            .isNotBlank()
        val fresh = staleness.count { it.value == Staleness.Fresh }
        val stale = staleness.count { it.value == Staleness.Stale }
        val gone = staleness.count { it.value == Staleness.Gone }
        // peerCount prefers the richer liveness map; fall back to raw mDNS
        // entries before the tracker has seen its first heartbeat.
        val peers = if (staleness.isNotEmpty()) staleness.size else speakers.size
        MultiroomState(
            broadcastEnabled = broadcastEnabled == true,
            hasSecret = hasSecret,
            broadcastingAs = registeredName?.takeIf { broadcastEnabled == true },
            peerCount = peers,
            freshCount = fresh,
            staleCount = stale,
            goneCount = gone
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, MultiroomState.Empty)

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
