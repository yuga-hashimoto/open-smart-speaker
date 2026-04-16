package com.opensmarthome.speaker.ui.settings.sysinfo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opensmarthome.speaker.assistant.router.ConversationRouter
import com.opensmarthome.speaker.assistant.skills.SkillRegistry
import com.opensmarthome.speaker.data.db.MemoryDao
import com.opensmarthome.speaker.device.DeviceManager
import com.opensmarthome.speaker.tool.ToolExecutor
import com.opensmarthome.speaker.util.NetworkMonitor
import com.opensmarthome.speaker.voice.metrics.LatencyRecorder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val toolExecutor: ToolExecutor
) : ViewModel() {

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
        val loading: Boolean = false
    )

    private val _state = MutableStateFlow(Snapshot(null, 0, 0, emptyList(), 0, 0, 0, false, 0, loading = true))
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
                loading = false
            )
        }
    }
}
