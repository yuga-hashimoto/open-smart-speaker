package com.opendash.app.device

import com.opendash.app.device.model.CommandResult
import com.opendash.app.device.model.Device
import com.opendash.app.device.model.DeviceCommand
import com.opendash.app.device.model.DeviceType
import com.opendash.app.device.model.Room
import com.opendash.app.device.provider.DeviceProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber

class DeviceManager(
    private val providers: Set<DeviceProvider>,
    private val refreshIntervalMs: Long = 30000L
) {
    private val _devices = MutableStateFlow<Map<String, Device>>(emptyMap())
    val devices: StateFlow<Map<String, Device>> = _devices.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var refreshJob: Job? = null

    suspend fun start() {
        refreshAll()
        refreshJob = scope.launch {
            while (isActive) {
                delay(refreshIntervalMs)
                refreshAll()
            }
        }
        // Merge state change flows from all providers
        providers.forEach { provider ->
            scope.launch {
                provider.stateChanges().collect { state ->
                    val current = _devices.value.toMutableMap()
                    val existing = current[state.deviceId]
                    if (existing != null) {
                        current[state.deviceId] = existing.copy(state = state)
                        _devices.value = current
                    }
                }
            }
        }
    }

    fun stop() {
        refreshJob?.cancel()
        refreshJob = null
    }

    suspend fun refreshAll() {
        val allDevices = mutableMapOf<String, Device>()
        for (provider in providers) {
            try {
                if (provider.isAvailable()) {
                    val devices = provider.getDevices()
                    devices.forEach { allDevices[it.id] = it }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to refresh devices from ${provider.id}")
            }
        }
        _devices.value = allDevices
        Timber.d("Device cache refreshed: ${allDevices.size} devices from ${providers.size} providers")
    }

    suspend fun executeCommand(command: DeviceCommand): CommandResult {
        val device = _devices.value[command.deviceId]
            ?: return CommandResult(false, "Device not found: ${command.deviceId}")
        val provider = providers.find { it.id == device.providerId }
            ?: return CommandResult(false, "Provider not found: ${device.providerId}")
        return try {
            provider.executeCommand(command)
        } catch (e: Exception) {
            Timber.e(e, "Command execution failed")
            CommandResult(false, e.message)
        }
    }

    fun getDevice(deviceId: String): Device? = _devices.value[deviceId]

    fun getDevicesByType(type: DeviceType): List<Device> =
        _devices.value.values.filter { it.type == type }

    fun getDevicesByRoom(room: String): List<Device> =
        _devices.value.values.filter { it.room.equals(room, ignoreCase = true) }

    fun getRooms(): List<Room> =
        _devices.value.values
            .mapNotNull { it.room }
            .distinct()
            .map { Room(id = it.lowercase().replace(" ", "_"), name = it) }
}
