package com.opensmarthome.speaker.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.opensmarthome.speaker.ui.ambient.ActiveTimersCard
import com.opensmarthome.speaker.ui.common.SuggestionBubble
import com.opensmarthome.speaker.ui.common.isExpandedLandscape
import com.opensmarthome.speaker.ui.theme.SpeakerBackground
import kotlinx.coroutines.delay
import java.time.LocalDateTime

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel()
) {
    // Clock tick lives in the Composable so HomeViewModel stays testable
    // (a perpetual delay loop inside viewModelScope would hang runTest).
    var time by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            time = LocalDateTime.now()
            delay(1000L)
        }
    }
    val weather by viewModel.weather.collectAsState()
    val onlineWeather by viewModel.onlineWeather.collectAsState()
    val headlines by viewModel.headlines.collectAsState()
    val chips by viewModel.deviceChips.collectAsState()
    val nowPlaying by viewModel.nowPlaying.collectAsState()
    val suggestions by viewModel.suggestions.collectAsState()
    val activeTimers by viewModel.activeTimers.collectAsState()
    val thermal by viewModel.thermalLevel.collectAsState()
    val nextEvent by viewModel.nextEvent.collectAsState()

    val wide = isExpandedLandscape()

    // `systemBarsPadding()` guards the standalone usage path (previews, direct
    // navigation) — when HomeScreen is hosted inside ModeScaffold the outer Box
    // has already consumed the insets so this becomes a no-op.
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SpeakerBackground)
            .systemBarsPadding()
    ) {
        if (wide) {
            // Tablet landscape: two-column top block with clock+online
            // weather on the left and status cards on the right, plus a
            // full-width headlines strip across the bottom. Mirrors the
            // Alexa / Echo Show front-tile rhythm: time-first, briefing
            // second, glanceable status third.
            Column(
                modifier = Modifier.fillMaxSize().padding(32.dp)
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1.2f).fillMaxHeight(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.Start
                    ) {
                        GreetingLine(time = time)
                        Spacer(modifier = Modifier.height(6.dp))
                        ClockWidget(time = time)
                        Spacer(modifier = Modifier.height(20.dp))
                        WeatherBlock(state = onlineWeather, sensorWeather = weather)
                    }
                    Spacer(Modifier.width(24.dp))
                    Column(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        verticalArrangement = Arrangement.Center
                    ) {
                        nextEvent?.let {
                            NextEventCard(event = it)
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                        if (activeTimers.isNotEmpty()) {
                            ActiveTimersCard(
                                timers = activeTimers,
                                onCancelTimer = viewModel::onCancelTimer
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                        if (chips.isNotEmpty()) {
                            DeviceStatusChips(chips = chips)
                        }
                    }
                }
                HeadlinesBlock(
                    state = headlines,
                    modifier = Modifier.padding(top = 20.dp),
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.weight(0.2f))
                GreetingLine(time = time)
                Spacer(modifier = Modifier.height(6.dp))
                ClockWidget(time = time)
                Spacer(modifier = Modifier.height(28.dp))
                WeatherBlock(state = onlineWeather, sensorWeather = weather)
                nextEvent?.let {
                    Spacer(modifier = Modifier.height(20.dp))
                    NextEventCard(event = it)
                }
                if (activeTimers.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(24.dp))
                    ActiveTimersCard(
                        timers = activeTimers,
                        onCancelTimer = viewModel::onCancelTimer
                    )
                }
                Spacer(modifier = Modifier.height(36.dp))
                if (chips.isNotEmpty()) {
                    DeviceStatusChips(chips = chips)
                }
                HeadlinesBlock(
                    state = headlines,
                    modifier = Modifier.padding(top = 24.dp),
                )
                Spacer(modifier = Modifier.weight(1f))
            }
        }

        val playing = nowPlaying
        if (playing != null) {
            NowPlayingBar(
                nowPlaying = playing,
                onMediaAction = { action ->
                    viewModel.dispatchMediaAction(playing.deviceId, action)
                },
                onVolumeChange = { level ->
                    viewModel.dispatchMediaVolume(playing.deviceId, level)
                },
                onShuffleToggle = { enabled ->
                    viewModel.dispatchShuffle(playing.deviceId, enabled)
                },
                onRepeatChange = { mode ->
                    viewModel.dispatchRepeat(playing.deviceId, mode)
                },
                onSourceSelected = { source ->
                    viewModel.dispatchSelectSource(playing.deviceId, source)
                },
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp)
            )
        }

        // Top-right (offset to avoid the settings gear): thermal throttle
        // badge only. Renders nothing in the common-case NORMAL state.
        // Battery chip was removed because it overlapped with the gear.
        TabletStatusChips(
            thermal = thermal,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 16.dp, end = 64.dp)
        )

        // Top: most recent proactive suggestion (one at a time so it doesn't
        // dominate the ambient view).
        suggestions.firstOrNull()?.let { suggestion ->
            SuggestionBubble(
                suggestion = suggestion,
                onAccept = { viewModel.acceptSuggestion(it) },
                onDismiss = { viewModel.dismissSuggestion(it.id) },
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }
}

/**
 * Routes an online-weather [BriefingState] to the right variant of the
 * weather tile. On success with data we show the full Alexa-style card;
 * on success with null we fall back to the sensor-based [WeatherWidget]
 * (HA / SwitchBot-provided temps) so the tile doesn't disappear when
 * online data is merely unavailable but local data is present. On
 * Loading / Error we show the matching skeleton / explainer card.
 */
@Composable
private fun WeatherBlock(
    state: BriefingState<com.opensmarthome.speaker.tool.info.WeatherInfo?>,
    sensorWeather: WeatherData?,
    modifier: Modifier = Modifier,
) {
    when (state) {
        BriefingState.Loading -> OnlineWeatherCardLoading(modifier = modifier)
        is BriefingState.Success -> {
            val info = state.data
            if (info != null) {
                OnlineWeatherCard(weather = info, modifier = modifier)
            } else {
                WeatherWidget(weather = sensorWeather, modifier = modifier)
            }
        }
        is BriefingState.Error -> OnlineWeatherCardError(
            kind = state.kind,
            modifier = modifier,
        )
    }
}

/**
 * Routes a headlines [BriefingState] to the right variant of the tile
 * strip. Success-with-empty collapses to nothing (legitimate "no news
 * today"); Loading and Error are explicit so users can tell the fetch
 * itself failed.
 */
@Composable
private fun HeadlinesBlock(
    state: BriefingState<List<com.opensmarthome.speaker.tool.info.NewsItem>>,
    modifier: Modifier = Modifier,
) {
    when (state) {
        BriefingState.Loading -> HeadlinesCardLoading(modifier = modifier)
        is BriefingState.Success -> {
            if (state.data.isNotEmpty()) {
                HeadlinesCard(headlines = state.data, modifier = modifier)
            }
        }
        is BriefingState.Error -> HeadlinesCardError(
            kind = state.kind,
            modifier = modifier,
        )
    }
}
