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
    val chips by viewModel.deviceChips.collectAsState()
    val nowPlaying by viewModel.nowPlaying.collectAsState()
    val suggestions by viewModel.suggestions.collectAsState()
    val activeTimers by viewModel.activeTimers.collectAsState()

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
            Row(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1.2f).fillMaxHeight(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.Start
                ) {
                    ClockWidget(time = time)
                    Spacer(modifier = Modifier.height(20.dp))
                    WeatherWidget(weather = weather)
                }
                Spacer(Modifier.width(24.dp))
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    verticalArrangement = Arrangement.Center
                ) {
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
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.weight(0.2f))
                ClockWidget(time = time)
                Spacer(modifier = Modifier.height(28.dp))
                WeatherWidget(weather = weather)
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
