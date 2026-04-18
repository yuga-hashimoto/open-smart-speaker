package com.opendash.app.ui.ambient

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opendash.app.device.DeviceManager
import com.opendash.app.multiroom.AnnouncementState
import com.opendash.app.tool.ToolCall
import com.opendash.app.tool.ToolExecutor
import com.opendash.app.tool.system.TimerInfo
import com.opendash.app.tool.system.TimerManager
import com.opendash.app.util.BatteryMonitor
import com.opendash.app.util.MulticastDiscovery
import com.opendash.app.util.ThermalLevel
import com.opendash.app.util.ThermalMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
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
    private val announcementState: AnnouncementState,
    private val timerManager: TimerManager
) : ViewModel() {

    private val _snapshot = MutableStateFlow(AmbientSnapshot(nowMs = System.currentTimeMillis()))
    val snapshot: StateFlow<AmbientSnapshot> = _snapshot.asStateFlow()

    /** IDs the user just tapped cancel on; filtered from [activeTimers] until the next poll drops them. */
    private val pendingCancelled = MutableStateFlow<Set<String>>(emptySet())

    /**
     * Live list of active timers, polled once a second so the mm:ss display
     * stays accurate without requiring TimerManager to expose a Flow.
     *
     * `WhileSubscribed(5_000)` means the polling loop only runs while at
     * least one collector is attached (the AmbientScreen). When the screen
     * leaves the foreground the polling pauses — saving a wake per second
     * on the tablet battery. The 5 s grace avoids churn across config
     * changes (rotation, etc.).
     *
     * [pendingCancelled] is overlaid so that tapping "cancel" removes the
     * row instantly rather than waiting up to ~1 s for the next poll.
     *
     * Empty = card hidden by UI layer.
     */
    val activeTimers: StateFlow<List<TimerInfo>> = flow {
        while (true) {
            val list = runCatching { timerManager.getActiveTimers() }
                .getOrElse { emptyList() }
            emit(list)
            delay(1_000L)
        }
    }.combine(pendingCancelled) { list, cancelled ->
        if (cancelled.isEmpty()) list else list.filterNot { it.id in cancelled }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

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

    /**
     * Cancel the timer identified by [id]. The next 1 Hz poll tick removes
     * it from [activeTimers]; we also stash the id in [pendingCancelled]
     * so the card row disappears immediately on tap.
     */
    fun onCancelTimer(id: String) {
        pendingCancelled.value = pendingCancelled.value + id
        viewModelScope.launch {
            runCatching { timerManager.cancelTimer(id) }
                .onFailure { Timber.w(it, "Failed to cancel timer $id") }
        }
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
