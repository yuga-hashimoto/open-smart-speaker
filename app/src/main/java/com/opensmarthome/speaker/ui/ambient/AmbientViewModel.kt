package com.opensmarthome.speaker.ui.ambient

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opensmarthome.speaker.device.DeviceManager
import com.opensmarthome.speaker.multiroom.AnnouncementState
import com.opensmarthome.speaker.tool.ToolCall
import com.opensmarthome.speaker.tool.ToolExecutor
import com.opensmarthome.speaker.util.BatteryMonitor
import com.opensmarthome.speaker.util.MulticastDiscovery
import com.opensmarthome.speaker.util.ThermalLevel
import com.opensmarthome.speaker.util.ThermalMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

/**
 * Exposes a single AmbientSnapshot state combining clock, weather, timers,
 * notifications and recent device activity for the Echo Show-style display.
 *
 * Also routes the ambient screen's quick-action buttons to the tool executor.
 */
@HiltViewModel
class AmbientViewModel @Inject constructor(
    private val deviceManager: DeviceManager,
    private val snapshotBuilder: AmbientSnapshotBuilder,
    private val toolExecutor: ToolExecutor,
    private val batteryMonitor: BatteryMonitor,
    private val thermalMonitor: ThermalMonitor,
    private val multicastDiscovery: MulticastDiscovery,
    private val announcementState: AnnouncementState
) : ViewModel() {

    private val _snapshot = MutableStateFlow(AmbientSnapshot(nowMs = System.currentTimeMillis()))
    val snapshot: StateFlow<AmbientSnapshot> = _snapshot.asStateFlow()

    init {
        viewModelScope.launch {
            // 5-arity combine: deviceMap, battery, thermal, peers, announcement.
            // AnnouncementState is an independent push source that can fire
            // without any other input changing, so it joins the existing
            // four flows here rather than via a side-channel.
            combine(
                deviceManager.devices,
                batteryMonitor.status,
                thermalMonitor.status,
                multicastDiscovery.speakers,
                announcementState.activeAnnouncement
            ) { deviceMap, battery, thermal, peers, announcement ->
                snapshotBuilder.build(deviceMap.values).copy(
                    batteryLevel = battery.level,
                    batteryCharging = battery.isCharging,
                    // Only surface a non-NORMAL state — ambient is a glance view;
                    // NORMAL should stay invisible so the chip isn't always lit.
                    thermalBucket = thermal.takeIf { it != ThermalLevel.NORMAL }?.name,
                    nearbySpeakerCount = peers.size,
                    announcementText = announcement?.text,
                    announcementFrom = announcement?.from
                )
            }.collect { _snapshot.value = it }
        }
    }

    /** Dismiss the currently-showing announcement banner (user tapped it). */
    fun dismissAnnouncement() {
        announcementState.clear()
    }

    /** Fire a quick-action button. Best-effort — logs failures, doesn't surface them. */
    fun runAction(toolName: String, arguments: Map<String, Any?> = emptyMap()) {
        viewModelScope.launch {
            try {
                toolExecutor.execute(
                    ToolCall(id = "ambient_${UUID.randomUUID()}", name = toolName, arguments = arguments)
                )
            } catch (e: Exception) {
                Timber.w(e, "Ambient quick action failed: $toolName")
            }
        }
    }
}
