package com.opensmarthome.speaker.ui.ambient

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.opensmarthome.speaker.ui.common.isExpandedLandscape
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
fun AmbientScreen(
    modifier: Modifier = Modifier,
    viewModel: AmbientViewModel = hiltViewModel()
) {
    val snapshot by viewModel.snapshot.collectAsState()
    var tick by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            tick++
            delay(1000L)
        }
    }

    val now = remember(tick) { LocalDateTime.now() }
    val wide = isExpandedLandscape()

    if (wide) {
        TwoColumnLayout(snapshot = snapshot, now = now, modifier = modifier)
    } else {
        SingleColumnLayout(snapshot = snapshot, now = now, modifier = modifier)
    }
}

@Composable
private fun SingleColumnLayout(
    snapshot: AmbientSnapshot,
    now: LocalDateTime,
    modifier: Modifier
) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        ClockBlock(snapshot = snapshot, now = now, centered = true)
        Spacer(modifier = Modifier.height(24.dp))
        WeatherStrip(snapshot)
        Spacer(modifier = Modifier.height(16.dp))
        CountsStrip(snapshot)
        if (snapshot.recentDeviceActivity.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            DeviceActivityCard(snapshot)
        }
    }
}

@Composable
private fun TwoColumnLayout(
    snapshot: AmbientSnapshot,
    now: LocalDateTime,
    modifier: Modifier
) {
    Row(
        modifier = modifier.fillMaxSize().padding(32.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: big clock + greeting
        Column(
            modifier = Modifier.weight(1.2f).fillMaxHeight(),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.Center
        ) {
            ClockBlock(snapshot = snapshot, now = now, centered = false)
        }

        Spacer(Modifier.width(24.dp))

        // Right: info stack
        Column(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            verticalArrangement = Arrangement.Center
        ) {
            WeatherStrip(snapshot)
            Spacer(Modifier.height(16.dp))
            CountsStrip(snapshot)
            if (snapshot.recentDeviceActivity.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                DeviceActivityCard(snapshot)
            }
        }
    }
}

@Composable
private fun ClockBlock(snapshot: AmbientSnapshot, now: LocalDateTime, centered: Boolean) {
    val alignment = if (centered) Alignment.CenterHorizontally else Alignment.Start
    Column(horizontalAlignment = alignment) {
        Text(
            text = snapshot.greeting(),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = now.format(DateTimeFormatter.ofPattern("HH:mm")),
            style = MaterialTheme.typography.displayLarge
        )
        Text(
            text = now.format(DateTimeFormatter.ofPattern("yyyy/MM/dd (E)")),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun WeatherStrip(snapshot: AmbientSnapshot) {
    val hasAny = snapshot.weatherCondition?.isNotBlank() == true ||
        snapshot.temperatureC != null ||
        snapshot.humidityPercent != null
    if (!hasAny) return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        snapshot.weatherCondition?.takeIf { it.isNotBlank() }?.let { cond ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Cloud, null, modifier = Modifier.padding(end = 4.dp))
                Text(cond.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.titleMedium)
            }
        }
        snapshot.temperatureC?.let { t ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Thermostat, null, modifier = Modifier.padding(end = 4.dp))
                Text("${"%.1f".format(t)}°", style = MaterialTheme.typography.titleMedium)
            }
        }
        snapshot.humidityPercent?.let { h ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.WaterDrop, null, modifier = Modifier.padding(end = 4.dp))
                Text("$h%", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun CountsStrip(snapshot: AmbientSnapshot) {
    if (snapshot.activeTimerCount == 0 && snapshot.activeNotificationCount == 0) return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        if (snapshot.activeTimerCount > 0) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Timer, null, modifier = Modifier.padding(end = 4.dp))
                Text(
                    text = "${snapshot.activeTimerCount} timer${if (snapshot.activeTimerCount != 1) "s" else ""}",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
        if (snapshot.activeNotificationCount > 0) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.NotificationsActive, null, modifier = Modifier.padding(end = 4.dp))
                Text(
                    text = "${snapshot.activeNotificationCount} notification${if (snapshot.activeNotificationCount != 1) "s" else ""}",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

@Composable
private fun DeviceActivityCard(snapshot: AmbientSnapshot) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Lightbulb, null, modifier = Modifier.padding(end = 8.dp))
                Text("Active devices", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(8.dp))
            snapshot.recentDeviceActivity.forEach { line ->
                Text(
                    text = "${line.name} · ${line.state}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
