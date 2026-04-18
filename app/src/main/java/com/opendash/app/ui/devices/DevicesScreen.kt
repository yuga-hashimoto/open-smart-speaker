package com.opendash.app.ui.devices

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.opendash.app.ui.theme.SpeakerBackground
import com.opendash.app.ui.theme.SpeakerTextSecondary

@Composable
fun DevicesScreen(
    modifier: Modifier = Modifier,
    viewModel: DevicesViewModel = hiltViewModel()
) {
    val roomGroups by viewModel.roomGroupedDevices.collectAsState()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(SpeakerBackground)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (roomGroups.isEmpty()) {
            item {
                Text(
                    text = "No devices connected",
                    style = MaterialTheme.typography.bodyLarge,
                    color = SpeakerTextSecondary,
                    modifier = Modifier.padding(32.dp)
                )
            }
        }

        roomGroups.forEach { (room, devices) ->
            item {
                Text(
                    text = room,
                    style = MaterialTheme.typography.titleMedium,
                    color = SpeakerTextSecondary,
                    modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp)
                )
            }
            items(devices.chunked(2), key = { it.map { d -> d.id }.joinToString() }) { rowDevices ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowDevices.forEach { device ->
                        RoomDeviceCard(
                            device = device,
                            onToggle = { viewModel.toggleDevice(it) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (rowDevices.size == 1) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
