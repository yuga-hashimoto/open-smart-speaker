package com.opensmarthome.speaker.ui.ambient

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opensmarthome.speaker.device.DeviceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Exposes a single AmbientSnapshot state combining clock, weather, timers,
 * notifications and recent device activity for the Echo Show-style display.
 */
@HiltViewModel
class AmbientViewModel @Inject constructor(
    private val deviceManager: DeviceManager,
    private val snapshotBuilder: AmbientSnapshotBuilder
) : ViewModel() {

    private val _snapshot = MutableStateFlow(AmbientSnapshot(nowMs = System.currentTimeMillis()))
    val snapshot: StateFlow<AmbientSnapshot> = _snapshot.asStateFlow()

    init {
        viewModelScope.launch {
            deviceManager.devices.collect { deviceMap ->
                _snapshot.value = snapshotBuilder.build(deviceMap.values)
            }
        }
    }
}
