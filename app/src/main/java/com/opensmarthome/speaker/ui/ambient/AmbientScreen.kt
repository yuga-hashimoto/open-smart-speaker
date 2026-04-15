package com.opensmarthome.speaker.ui.ambient

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.WaterDrop
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
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
fun AmbientScreen(
    modifier: Modifier = Modifier,
    viewModel: AmbientViewModel = hiltViewModel()
) {
    var currentTime by remember { mutableStateOf(LocalDateTime.now()) }
    val weatherState by viewModel.weatherState.collectAsState()
    val temperature by viewModel.temperature.collectAsState()
    val humidity by viewModel.humidity.collectAsState()

    LaunchedEffect(Unit) {
        while (true) {
            currentTime = LocalDateTime.now()
            delay(1000L)
        }
    }

    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = currentTime.format(DateTimeFormatter.ofPattern("HH:mm")),
            style = MaterialTheme.typography.displayLarge
        )
        Text(
            text = currentTime.format(DateTimeFormatter.ofPattern("yyyy/MM/dd (E)")),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        if (weatherState.isNotBlank()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Cloud, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                    Text(
                        text = weatherState.replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                if (temperature.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Thermostat, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                        Text(text = "${temperature}°", style = MaterialTheme.typography.titleMedium)
                    }
                }
                if (humidity.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.WaterDrop, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                        Text(text = "${humidity}%", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}
