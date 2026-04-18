package com.opendash.app.ui.ambient

import com.opendash.app.device.model.Device
import com.opendash.app.device.model.DeviceState
import com.opendash.app.tool.system.NotificationProvider
import com.opendash.app.tool.system.TimerManager

/**
 * Assembles an AmbientSnapshot from live sources. Kept as a pure builder
 * with explicit dependencies so unit tests can drive it without Android runtime.
 */
class AmbientSnapshotBuilder(
    private val timerManager: TimerManager? = null,
    private val notificationProvider: NotificationProvider? = null,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {

    suspend fun build(devices: Collection<Device>): AmbientSnapshot {
        val weatherDevice = devices.firstOrNull {
            it.state.temperature != null || it.state.humidity != null
        }
        val tempC = weatherDevice?.state?.temperature?.toDouble()
        val humidity = weatherDevice?.state?.humidity?.toInt()
        val condition = weatherDevice?.state?.attributes?.get("state") as? String

        val timers = runCatching { timerManager?.getActiveTimers() ?: emptyList() }.getOrElse { emptyList() }
        val notifications = notificationProvider?.takeIf { it.isListenerEnabled() }?.let { np ->
            runCatching { np.listNotifications() }.getOrElse { emptyList() }
        }.orEmpty()

        val deviceActivity = devices
            .filter { it.state.isOn == true || it.state.mediaTitle?.isNotBlank() == true }
            .sortedByDescending { it.state.lastUpdated }
            .take(4)
            .map { d ->
                AmbientSnapshot.DeviceLine(
                    name = d.name,
                    state = describe(d.state)
                )
            }

        // Soonest-finishing timer surfaces as a single line so the user
        // sees what's next without opening the Timer app.
        val nextTimer = timers.minByOrNull { it.remainingSeconds }

        return AmbientSnapshot(
            nowMs = clock(),
            temperatureC = tempC,
            humidityPercent = humidity,
            weatherCondition = condition,
            activeNotificationCount = notifications.size,
            activeTimerCount = timers.size,
            nextTimerLabel = nextTimer?.label?.takeIf { it.isNotBlank() },
            nextTimerRemainingSeconds = nextTimer?.remainingSeconds,
            recentDeviceActivity = deviceActivity
        )
    }

    private fun describe(state: DeviceState): String {
        state.mediaTitle?.takeIf { it.isNotBlank() }?.let { return "playing $it" }
        state.isOn?.let { return if (it) "on" else "off" }
        state.temperature?.let { return "${"%.1f".format(it)}°" }
        return "idle"
    }
}
