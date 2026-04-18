package com.opendash.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opendash.app.device.DeviceManager
import com.opendash.app.device.model.Device
import com.opendash.app.device.model.DeviceCommand
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val deviceManager: DeviceManager
) : ViewModel() {

    val devices: StateFlow<Map<String, Device>> = deviceManager.devices

    private val _groupedDevices = MutableStateFlow<Map<String, List<Device>>>(emptyMap())
    val groupedDevices: StateFlow<Map<String, List<Device>>> = _groupedDevices.asStateFlow()

    val quickActions = listOf(
        QuickAction("All Lights Off", "light", "turn_off"),
        QuickAction("All Lights On", "light", "turn_on"),
    )

    init {
        viewModelScope.launch { deviceManager.start() }
        viewModelScope.launch {
            deviceManager.devices.collect { deviceMap ->
                _groupedDevices.value = deviceMap.values
                    .filter { it.type.value in listOf("light", "switch", "climate", "media_player", "cover", "fan", "input_boolean") }
                    .groupBy { it.type.value }
            }
        }
    }

    fun toggleDevice(device: Device) {
        viewModelScope.launch {
            try {
                val action = if (device.state.isOn == true) "turn_off" else "turn_on"
                deviceManager.executeCommand(
                    DeviceCommand(deviceId = device.id, action = action)
                )
                deviceManager.refreshAll()
            } catch (e: Exception) {
                Timber.e(e, "Failed to toggle ${device.id}")
            }
        }
    }

    fun setBrightness(device: Device, brightness: Float) {
        viewModelScope.launch {
            try {
                deviceManager.executeCommand(
                    DeviceCommand(
                        deviceId = device.id,
                        action = "turn_on",
                        parameters = mapOf("brightness" to brightness.toInt())
                    )
                )
            } catch (e: Exception) {
                Timber.e(e, "Failed to set brightness for ${device.id}")
            }
        }
    }

    fun executeQuickAction(action: QuickAction) {
        viewModelScope.launch {
            try {
                val devices = deviceManager.getDevicesByType(
                    com.opendash.app.device.model.DeviceType.fromString(action.domain)
                )
                for (device in devices) {
                    deviceManager.executeCommand(
                        DeviceCommand(deviceId = device.id, action = action.service)
                    )
                }
                deviceManager.refreshAll()
            } catch (e: Exception) {
                Timber.e(e, "Failed to execute quick action: ${action.label}")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        deviceManager.stop()
    }
}
