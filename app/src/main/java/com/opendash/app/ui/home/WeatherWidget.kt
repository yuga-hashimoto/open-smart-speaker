package com.opendash.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.opendash.app.ui.theme.DeviceClimate
import com.opendash.app.ui.theme.SpeakerSurfaceVariant
import com.opendash.app.ui.theme.SpeakerTextPrimary
import com.opendash.app.ui.theme.SpeakerTextSecondary

data class WeatherData(
    val temperature: String,
    val humidity: String
)

@Composable
fun WeatherWidget(weather: WeatherData?, modifier: Modifier = Modifier) {
    if (weather == null) return

    Row(
        modifier = modifier
            .background(SpeakerSurfaceVariant, RoundedCornerShape(16.dp))
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Thermostat,
                contentDescription = null,
                tint = DeviceClimate,
                modifier = Modifier.size(24.dp).padding(end = 6.dp)
            )
            Text(
                text = "${weather.temperature}\u00B0",
                style = MaterialTheme.typography.headlineLarge,
                color = SpeakerTextPrimary
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.WaterDrop,
                contentDescription = null,
                tint = SpeakerTextSecondary,
                modifier = Modifier.size(20.dp).padding(end = 6.dp)
            )
            Text(
                text = "${weather.humidity}%",
                style = MaterialTheme.typography.titleLarge,
                color = SpeakerTextSecondary
            )
        }
    }
}
