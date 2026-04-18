package com.opendash.app.ui.devices

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
class DevicesViewModel @Inject constructor(
    private val deviceManager: DeviceManager
) : ViewModel() {

    private val _roomGroupedDevices = MutableStateFlow<List<Pair<String, List<Device>>>>(emptyList())
    val roomGroupedDevices: StateFlow<List<Pair<String, List<Device>>>> = _roomGroupedDevices.asStateFlow()

    init {
        viewModelScope.launch { deviceManager.start() }
        viewModelScope.launch {
            deviceManager.devices.collect { deviceMap ->
                val controllable = deviceMap.values.filter {
                    it.type.value in listOf("light", "switch", "climate", "media_player", "cover", "fan", "lock")
                }
                _roomGroupedDevices.value = controllable
                    .groupBy { it.room ?: "Other" }
                    .toSortedMap()
                    .map { (room, devices) -> room to devices }
            }
        }
    }

    fun toggleDevice(device: Device) {
        viewModelScope.launch {
            try {
                val action = if (device.state.isOn == true) "turn_off" else "turn_on"
                deviceManager.executeCommand(DeviceCommand(deviceId = device.id, action = action))
                deviceManager.refreshAll()
            } catch (e: Exception) {
                Timber.e(e, "Failed to toggle ${device.id}")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        deviceManager.stop()
    }
}
