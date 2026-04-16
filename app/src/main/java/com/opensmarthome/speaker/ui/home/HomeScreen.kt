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
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.opensmarthome.speaker.ui.common.isExpandedLandscape
import com.opensmarthome.speaker.ui.theme.SpeakerBackground

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val time by viewModel.currentTime.collectAsState()
    val weather by viewModel.weather.collectAsState()
    val chips by viewModel.deviceChips.collectAsState()
    val nowPlaying by viewModel.nowPlaying.collectAsState()

    val wide = isExpandedLandscape()

    Box(
        modifier = modifier.fillMaxSize().background(SpeakerBackground)
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
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp)
            )
        }
    }
}
