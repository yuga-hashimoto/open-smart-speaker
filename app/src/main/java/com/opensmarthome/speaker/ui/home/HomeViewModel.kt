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
import com.opensmarthome.speaker.tool.info.NewsItem
import com.opensmarthome.speaker.tool.info.WeatherInfo
import com.opensmarthome.speaker.tool.system.CalendarEvent
import com.opensmarthome.speaker.tool.system.TimerInfo
import com.opensmarthome.speaker.tool.system.TimerManager
import com.opensmarthome.speaker.util.BatteryMonitor
import com.opensmarthome.speaker.util.BatteryStatus
import com.opensmarthome.speaker.util.ThermalLevel
import com.opensmarthome.speaker.util.ThermalMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
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
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val deviceManager: DeviceManager,
    private val suggestionState: SuggestionState,
    private val toolExecutor: ToolExecutor,
    private val timerManager: TimerManager,
    private val briefingSource: OnlineBriefingSource,
    batteryMonitor: BatteryMonitor,
    thermalMonitor: ThermalMonitor,
    private val upcomingEventSource: UpcomingEventSource,
) : ViewModel() {

    companion object {
        /** Weather API refresh cadence. Open-Meteo has no rate limit to speak of,
         *  but refreshing a current-conditions card more often than once every
         *  few minutes is just noise. 15 min matches Alexa's front-tile refresh. */
        internal const val WEATHER_REFRESH_MS = 15L * 60L * 1000L

        /** RSS feed refresh cadence. Most outlets update <= every 10-15 min;
         *  25 min leaves headroom without starving the UI on breaking news. */
        internal const val HEADLINES_REFRESH_MS = 25L * 60L * 1000L

        /** Headline tile count on the Home dashboard. Keeps the UI readable
         *  at a glance on a tablet; deeper browsing is a voice command. */
        internal const val HEADLINES_LIMIT = 5

        /** Cadence for polling the calendar provider. Five minutes is more
         *  than fast enough — next-event resolution is measured in minutes,
         *  not seconds, and calendar syncs are already coarse. */
        internal const val NEXT_EVENT_REFRESH_MS = 5L * 60L * 1000L
    }

    /**
     * Pass-through tablet-self battery + thermal state. Surfaces them on
     * the Home dashboard as a persistent chip strip so users can tell at
     * a glance that the device is healthy (battery-backed, cool) — the
     * same "always-on status" that Echo Show / Nest Hub expose as icons
     * in the status bar.
     */
    val batteryStatus: StateFlow<BatteryStatus> = batteryMonitor.status
    val thermalLevel: StateFlow<ThermalLevel> = thermalMonitor.status

    val suggestions: StateFlow<List<Suggestion>> = suggestionState.current

    /** IDs the user just tapped cancel on; filtered from [activeTimers] until the next poll drops them. */
    private val pendingCancelled = MutableStateFlow<Set<String>>(emptySet())

    /**
     * Live list of active timers, polled 1 Hz so the Home tab's mm:ss
     * countdown stays fresh without requiring TimerManager to expose a Flow.
     * Mirrors the pattern used by [com.opensmarthome.speaker.ui.ambient.AmbientViewModel];
     * kept as a private copy here rather than being pulled up into a shared
     * holder to avoid a DI refactor in a bugfix PR.
     *
     * `WhileSubscribed(5_000)` keeps polling paused while the Home tab is
     * not the foreground screen.
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

    fun dismissSuggestion(id: String) {
        suggestionState.dismiss(id)
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

    /**
     * Online current-conditions briefing (Open-Meteo). Polled on the UI
     * lifecycle — `WhileSubscribed(5_000)` pauses the loop when Home is
     * off-screen so a tablet left on the Settings tab isn't burning mobile
     * data.
     *
     * Emits [BriefingState.Loading] as the first visible value before any
     * fetch completes so the card renders a skeleton instead of being
     * invisible. On failure we emit [BriefingState.Error] so the dashboard
     * can explain what went wrong rather than silently collapsing the
     * tile — the old "`null` on error" shape was the root cause of users
     * reporting "weather just isn't showing up".
     */
    val onlineWeather: StateFlow<BriefingState<WeatherInfo?>> = flow {
        while (true) {
            val result = briefingSource.currentWeather()
            result.fold(
                onSuccess = { emit(BriefingState.Success(it)) },
                onFailure = { emit(BriefingState.Error(it.classify())) },
            )
            delay(WEATHER_REFRESH_MS)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = BriefingState.Loading
    )

    /**
     * Latest RSS headlines. Same lifecycle + error-visibility treatment
     * as [onlineWeather]. Empty success lists are emitted too (not
     * suppressed) so the UI can distinguish "fetched fine, feed empty"
     * from "fetch failed".
     */
    val headlines: StateFlow<BriefingState<List<NewsItem>>> = flow {
        while (true) {
            val result = briefingSource.latestHeadlines(HEADLINES_LIMIT)
            result.fold(
                onSuccess = { emit(BriefingState.Success(it)) },
                onFailure = { emit(BriefingState.Error(it.classify())) },
            )
            delay(HEADLINES_REFRESH_MS)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = BriefingState.Loading
    )

    /**
     * Single next upcoming calendar event. Polled every
     * [NEXT_EVENT_REFRESH_MS]; `WhileSubscribed(5_000)` pauses the loop
     * while Home is off-screen. Emits `null` when calendar permission
     * is missing or nothing is coming up.
     */
    val nextEvent: StateFlow<CalendarEvent?> = flow {
        while (true) {
            emit(upcomingEventSource.nextEvent())
            delay(NEXT_EVENT_REFRESH_MS)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null
    )

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

/**
 * Maps a thrown briefing failure to the coarse [BriefingState.Error.Kind]
 * the UI consumes. Kept narrow — the dashboard doesn't need to know the
 * exception type, just whether it can tell the user "offline" vs. "the
 * feed itself is broken".
 */
internal fun Throwable.classify(): BriefingState.Error.Kind = when (this) {
    is IOException -> BriefingState.Error.Kind.Network
    is IllegalStateException, is IllegalArgumentException -> BriefingState.Error.Kind.Parse
    else -> BriefingState.Error.Kind.Unknown
}
