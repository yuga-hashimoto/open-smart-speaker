package com.opensmarthome.speaker.ui.settings.sysinfo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opensmarthome.speaker.assistant.router.ConversationRouter
import com.opensmarthome.speaker.assistant.skills.SkillRegistry
import com.opensmarthome.speaker.data.db.MemoryDao
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
    private val peerLivenessTracker: PeerLivenessTracker
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
                loading = false
            )
        }
    }
}
