package com.opensmarthome.speaker.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opensmarthome.speaker.assistant.proactive.Suggestion
import com.opensmarthome.speaker.assistant.proactive.SuggestionState
import com.opensmarthome.speaker.device.DeviceManager
import com.opensmarthome.speaker.device.model.DeviceCommand
import com.opensmarthome.speaker.device.model.DeviceType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val deviceManager: DeviceManager,
    private val suggestionState: SuggestionState
) : ViewModel() {

    val suggestions: StateFlow<List<Suggestion>> = suggestionState.current

    fun dismissSuggestion(id: String) {
        suggestionState.dismiss(id)
    }

    private val _weather = MutableStateFlow<WeatherData?>(null)
    val weather: StateFlow<WeatherData?> = _weather.asStateFlow()

    private val _deviceChips = MutableStateFlow<List<DeviceChip>>(emptyList())
    val deviceChips: StateFlow<List<DeviceChip>> = _deviceChips.asStateFlow()

    private val _nowPlaying = MutableStateFlow<NowPlayingInfo?>(null)
    val nowPlaying: StateFlow<NowPlayingInfo?> = _nowPlaying.asStateFlow()

    init {
        viewModelScope.launch {
            deviceManager.devices.collect { devices ->
                // Weather from sensor devices
                val sensor = devices.values.firstOrNull {
                    it.state.temperature != null && it.state.humidity != null
                }
                _weather.value = sensor?.let {
                    WeatherData(
                        temperature = it.state.temperature?.let { t -> "%.0f".format(t) } ?: "",
                        humidity = it.state.humidity?.let { h -> "%.0f".format(h) } ?: ""
                    )
                }

                // Active device chips (lights on, climate, playing media)
                _deviceChips.value = devices.values
                    .filter { d ->
                        (d.state.isOn == true) ||
                        d.type == DeviceType.CLIMATE ||
                        (d.type == DeviceType.MEDIA_PLAYER && d.state.mediaTitle != null)
                    }
                    .take(4)
                    .map { d ->
                        DeviceChip(
                            name = d.name,
                            type = d.type,
                            isOn = d.state.isOn == true,
                            summary = when (d.type) {
                                DeviceType.LIGHT -> d.state.brightness?.let { "${(it / 255 * 100).toInt()}%" } ?: "On"
                                DeviceType.CLIMATE -> d.state.temperature?.let { "%.1f\u00B0".format(it) } ?: ""
                                DeviceType.MEDIA_PLAYER -> d.state.mediaTitle ?: ""
                                else -> if (d.state.isOn == true) "On" else "Off"
                            }
                        )
                    }

                // Now playing
                val mediaDevice = devices.values.firstOrNull {
                    it.type == DeviceType.MEDIA_PLAYER && it.state.mediaTitle != null
                }
                _nowPlaying.value = mediaDevice?.let {
                    NowPlayingInfo(
                        deviceId = it.id,
                        deviceName = it.name,
                        mediaTitle = it.state.mediaTitle,
                        mediaArtist = it.state.attributes["media_artist"] as? String,
                        isPlaying = it.state.isOn == true
                    )
                }
            }
        }
    }

    fun dispatchMediaAction(deviceId: String, action: MediaAction) {
        viewModelScope.launch {
            deviceManager.executeCommand(
                DeviceCommand(deviceId = deviceId, action = action.haService)
            )
        }
    }
}
