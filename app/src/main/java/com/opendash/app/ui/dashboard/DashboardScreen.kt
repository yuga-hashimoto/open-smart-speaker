package com.opendash.app.ui.dashboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun DashboardScreen(
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val grouped by viewModel.groupedDevices.collectAsState()

    Column(modifier = modifier.fillMaxSize()) {
        QuickActionRow(
            actions = viewModel.quickActions,
            onAction = { viewModel.executeQuickAction(it) }
        )

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 180.dp),
            modifier = Modifier.fillMaxSize().padding(8.dp)
        ) {
            grouped.forEach { (type, devices) ->
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = type.replaceFirstChar { it.uppercase() }.replace("_", " "),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
                    )
                }
                items(devices, key = { it.id }) { device ->
                    DeviceCard(
                        device = device,
                        onToggle = { viewModel.toggleDevice(it) },
                        onBrightnessChange = { d, b -> viewModel.setBrightness(d, b) }
                    )
                }
            }
        }
    }
}
