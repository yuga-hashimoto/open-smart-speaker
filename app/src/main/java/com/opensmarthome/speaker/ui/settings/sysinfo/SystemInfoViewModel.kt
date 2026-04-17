package com.opensmarthome.speaker.ui.settings.sysinfo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opensmarthome.speaker.assistant.router.ConversationRouter
import com.opensmarthome.speaker.assistant.skills.SkillRegistry
import com.opensmarthome.speaker.data.db.MemoryDao
import com.opensmarthome.speaker.data.db.MultiroomRejectionDao
import com.opensmarthome.speaker.data.db.MultiroomTrafficDao
import com.opensmarthome.speaker.data.db.MultiroomTrafficEntity
import com.opensmarthome.speaker.data.db.RoutineDao
import com.opensmarthome.speaker.device.DeviceManager
import com.opensmarthome.speaker.multiroom.PeerFreshness
import com.opensmarthome.speaker.multiroom.PeerLivenessTracker
import com.opensmarthome.speaker.tool.ToolExecutor
import com.opensmarthome.speaker.tool.rag.RagRepository
import com.opensmarthome.speaker.util.DiscoveredSpeaker
import com.opensmarthome.speaker.util.MulticastDiscovery
import com.opensmarthome.speaker.util.NetworkMonitor
import com.opensmarthome.speaker.util.ThermalMonitor
import com.opensmarthome.speaker.voice.metrics.LatencyRecorder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Read-only "About this assistant" snapshot — model, device count, skill
 * count, memory entry count, online/offline, latency budget violations.
 */
@HiltViewModel
class SystemInfoViewModel @Inject constructor(
    private val deviceManager: DeviceManager,
    private val skillRegistry: SkillRegistry,
    private val memoryDao: MemoryDao,
    private val router: ConversationRouter,
    private val networkMonitor: NetworkMonitor,
    private val latencyRecorder: LatencyRecorder,
    private val toolExecutor: ToolExecutor,
    private val routineDao: RoutineDao,
    private val ragRepository: RagRepository,
    private val multicastDiscovery: MulticastDiscovery,
    private val thermalMonitor: ThermalMonitor,
    private val peerLivenessTracker: PeerLivenessTracker,
    private val multiroomTrafficDao: MultiroomTrafficDao,
    private val multiroomRejectionDao: MultiroomRejectionDao
) : ViewModel() {

    val nearbySpeakers: StateFlow<List<DiscoveredSpeaker>> = multicastDiscovery.speakers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val registeredName: StateFlow<String?> = multicastDiscovery.registeredName
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Per-peer freshness map keyed by mDNS serviceName. UI renders each
     * discovered peer's suffix (fresh / stale / gone) from this.
     */
    val peerFreshness: StateFlow<Map<String, PeerFreshness>> = peerLivenessTracker.staleness
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Start (idempotent) mDNS discovery. Callers trigger this when the screen is visible. */
    fun startDiscovery() = multicastDiscovery.start()

    /** Stop mDNS discovery to release the network stack. */
    fun stopDiscovery() = multicastDiscovery.stop()

    /**
     * One row per observed envelope type in the lifetime
     * [MultiroomTrafficEntity] table. [inbound] / [outbound] are
     * independent counters (different DB rows), collapsed here for UI
     * consumption; [lastAtMs] is the most recent touch across both
     * directions.
     */
    data class TrafficRow(
        val type: String,
        val inbound: Long,
        val outbound: Long,
        val lastAtMs: Long
    )

    /**
     * One row per observed rejection reason from
     * [com.opensmarthome.speaker.multiroom.AnnouncementParser.Reason].
     * [hint] is a short human-friendly diagnostic the UI renders
     * alongside the count.
     */
    data class RejectionRow(
        val reason: String,
        val count: Long,
        val lastAtMs: Long,
        val hint: String
    )

    data class Snapshot(
        val activeProviderModel: String?,
        val providerCount: Int,
        val deviceCount: Int,
        val devicesByType: List<Pair<String, Int>> = emptyList(),
        val skillCount: Int,
        val memoryCount: Int,
        val toolCount: Int,
        val online: Boolean,
        val totalBudgetViolations: Int,
        val totalLatencyMeasurements: Long = 0L,
        val routineCount: Int = 0,
        val documentCount: Int = 0,
        val thermalLevel: String = "NORMAL",
        val multiroomTraffic: List<TrafficRow> = emptyList(),
        val rejections: List<RejectionRow> = emptyList(),
        val loading: Boolean = false
    )

    private val _state = MutableStateFlow(
        Snapshot(null, 0, 0, emptyList(), 0, 0, 0, false, 0, 0L, 0, 0, loading = true)
    )
    val state: StateFlow<Snapshot> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val deviceMap = deviceManager.devices.value
            val byType = deviceMap.values
                .groupBy { it.type.value }
                .map { (type, list) -> type to list.size }
                .sortedByDescending { it.second }
            val skills = skillRegistry.all().size
            val memories = memoryDao.list(1000).size
            val tools = toolExecutor.availableTools().size
            val violations = latencyRecorder.budgetViolations().values.sum()
            val measurements = latencyRecorder.totalMeasurements()
            val routines = runCatching { routineDao.listAll().size }.getOrDefault(0)
            val documents = runCatching { ragRepository.listDocuments().size }.getOrDefault(0)
            val traffic = runCatching { multiroomTrafficDao.listAll() }.getOrDefault(emptyList())
            val rejections = runCatching { multiroomRejectionDao.listAll() }.getOrDefault(emptyList())
            val rejectionRows = rejections
                .map { row ->
                    RejectionRow(
                        reason = row.reason,
                        count = row.count,
                        lastAtMs = row.lastAtMs,
                        hint = hintForReason(row.reason)
                    )
                }
                .sortedByDescending { it.count }
            val trafficRows = traffic
                .groupBy { it.type }
                .map { (type, rows) ->
                    TrafficRow(
                        type = type,
                        inbound = rows.firstOrNull { it.direction == MultiroomTrafficEntity.DIRECTION_IN }?.count ?: 0L,
                        outbound = rows.firstOrNull { it.direction == MultiroomTrafficEntity.DIRECTION_OUT }?.count ?: 0L,
                        lastAtMs = rows.maxOf { it.lastAtMs }
                    )
                }
                .sortedBy { it.type }
            _state.value = Snapshot(
                activeProviderModel = router.activeProvider.value?.capabilities?.modelName,
                providerCount = router.availableProviders.value.size,
                deviceCount = deviceMap.size,
                devicesByType = byType,
                skillCount = skills,
                memoryCount = memories,
                toolCount = tools,
                online = networkMonitor.isOnline.value,
                totalBudgetViolations = violations,
                totalLatencyMeasurements = measurements,
                routineCount = routines,
                documentCount = documents,
                thermalLevel = thermalMonitor.status.value.name,
                multiroomTraffic = trafficRows,
                rejections = rejectionRows,
                loading = false
            )
        }
    }

    /**
     * Human-friendly diagnostic for each
     * [com.opensmarthome.speaker.multiroom.AnnouncementParser.Reason].
     * Rendered alongside the count so the user has a pointer at what
     * probably needs to be checked.
     */
    private fun hintForReason(reason: String): String = when (reason) {
        "MALFORMED_JSON" -> "malformed JSON — peer is sending invalid frames"
        "MISSING_FIELD" -> "missing required envelope field"
        "VERSION_MISMATCH" -> "protocol version mismatch — peers on different builds"
        "REPLAY_WINDOW" -> "outside replay window — check device clocks"
        "NO_SECRET" -> "shared secret not configured on this device"
        "HMAC_MISMATCH" -> "HMAC mismatch — check shared secret"
        else -> reason.lowercase().replace('_', ' ')
    }
}
