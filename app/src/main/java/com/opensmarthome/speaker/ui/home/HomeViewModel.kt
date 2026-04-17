package com.opensmarthome.speaker.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opensmarthome.speaker.assistant.proactive.Suggestion
import com.opensmarthome.speaker.assistant.proactive.SuggestionState
import com.opensmarthome.speaker.device.DeviceManager
import com.opensmarthome.speaker.device.model.DeviceCommand
import com.opensmarthome.speaker.device.model.DeviceType
import com.opensmarthome.speaker.tool.ToolCall
import com.opensmarthome.speaker.tool.ToolExecutor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val deviceManager: DeviceManager,
    private val suggestionState: SuggestionState,
    private val toolExecutor: ToolExecutor
) : ViewModel() {

    val suggestions: StateFlow<List<Suggestion>> = suggestionState.current

    fun dismissSuggestion(id: String) {
        suggestionState.dismiss(id)
    }

    /**
     * "Yes" on a SuggestionBubble: execute the suggested tool call, then
     * dismiss the bubble. Best-effort — if the tool fails the user can still
     * try via voice or manually.
     */
    fun acceptSuggestion(suggestion: Suggestion) {
        val action = suggestion.suggestedAction
        if (action == null) {
            suggestionState.dismiss(suggestion.id)
            return
        }
        viewModelScope.launch {
            try {
                toolExecutor.execute(
                    ToolCall(
                        id = "suggest_${suggestion.id}",
                        name = action.toolName,
                        arguments = action.arguments
                    )
                )
            } catch (e: Exception) {
                Timber.w(e, "Suggested action failed: ${action.toolName}")
            }
            suggestionState.dismiss(suggestion.id)
        }
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
                    val sources = (it.state.attributes["source_list"] as? List<*>)
                        ?.mapNotNull { s -> s as? String }
                        ?: emptyList()
                    NowPlayingInfo(
                        deviceId = it.id,
                        deviceName = it.name,
                        mediaTitle = it.state.mediaTitle,
                        mediaArtist = it.state.attributes["media_artist"] as? String,
                        isPlaying = it.state.isOn == true,
                        volumeLevel = (it.state.attributes["volume_level"] as? Number)?.toFloat(),
                        shuffle = it.state.attributes["shuffle"] as? Boolean,
                        repeatMode = (it.state.attributes["repeat"] as? String)?.let { r -> RepeatMode.fromHa(r) },
                        playlist = it.state.attributes["media_playlist"] as? String,
                        sources = sources
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

    fun dispatchMediaVolume(deviceId: String, level: Float) {
        val clamped = level.coerceIn(0f, 1f)
        viewModelScope.launch {
            deviceManager.executeCommand(
                DeviceCommand(
                    deviceId = deviceId,
                    action = "volume_set",
                    parameters = mapOf("volume_level" to clamped)
                )
            )
        }
    }

    fun dispatchShuffle(deviceId: String, enabled: Boolean) {
        viewModelScope.launch {
            deviceManager.executeCommand(
                DeviceCommand(
                    deviceId = deviceId,
                    action = "shuffle_set",
                    parameters = mapOf("shuffle" to enabled)
                )
            )
        }
    }

    fun dispatchRepeat(deviceId: String, mode: RepeatMode) {
        viewModelScope.launch {
            deviceManager.executeCommand(
                DeviceCommand(
                    deviceId = deviceId,
                    action = "repeat_set",
                    parameters = mapOf("repeat" to mode.haValue)
                )
            )
        }
    }

    /** Fires HA `media_player.select_source` with the chosen source name. */
    fun dispatchSelectSource(deviceId: String, source: String) {
        viewModelScope.launch {
            deviceManager.executeCommand(
                DeviceCommand(
                    deviceId = deviceId,
                    action = "select_source",
                    parameters = mapOf("source" to source)
                )
            )
        }
    }
}
