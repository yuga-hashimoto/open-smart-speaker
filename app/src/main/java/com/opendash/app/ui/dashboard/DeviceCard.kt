package com.opendash.app.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.opendash.app.device.model.Device
import com.opendash.app.device.model.DeviceType

@Composable
fun DeviceCard(
    device: Device,
    onToggle: (Device) -> Unit,
    onBrightnessChange: (Device, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth().padding(4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        when (device.type) {
            DeviceType.LIGHT -> LightDeviceCard(device, onToggle, onBrightnessChange)
            DeviceType.SWITCH -> SwitchDeviceCard(device, onToggle)
            DeviceType.CLIMATE -> ClimateDeviceCard(device)
            DeviceType.MEDIA_PLAYER -> MediaPlayerDeviceCard(device)
            else -> GenericDeviceCard(device)
        }
    }
}

@Composable
private fun LightDeviceCard(device: Device, onToggle: (Device) -> Unit, onBrightnessChange: (Device, Float) -> Unit) {
    Column(modifier = Modifier.padding(12.dp)) {
        DeviceHeader(device, Icons.Filled.Lightbulb)
        Switch(checked = device.state.isOn == true, onCheckedChange = { onToggle(device) })
        val brightness = device.state.brightness
        if (brightness != null && device.state.isOn == true) {
            Slider(
                value = brightness / 255f,
                onValueChange = { onBrightnessChange(device, it * 255f) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun SwitchDeviceCard(device: Device, onToggle: (Device) -> Unit) {
    Column(modifier = Modifier.padding(12.dp)) {
        DeviceHeader(device, Icons.Filled.Power)
        Switch(checked = device.state.isOn == true, onCheckedChange = { onToggle(device) })
    }
}

@Composable
private fun ClimateDeviceCard(device: Device) {
    Column(modifier = Modifier.padding(12.dp)) {
        DeviceHeader(device, Icons.Filled.Thermostat)
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("Temp: ${device.state.temperature ?: "?"}°", style = MaterialTheme.typography.bodyMedium)
            device.state.humidity?.let { Text("Humidity: $it%", style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

@Composable
private fun MediaPlayerDeviceCard(device: Device) {
    Column(modifier = Modifier.padding(12.dp)) {
        DeviceHeader(device, Icons.Filled.PlayArrow)
        device.state.mediaTitle?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun GenericDeviceCard(device: Device) {
    Column(modifier = Modifier.padding(12.dp)) {
        Text(device.name, style = MaterialTheme.typography.titleSmall)
        Text(if (device.state.isOn == true) "On" else "Off", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun DeviceHeader(device: Device, icon: ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
        Icon(
            icon, contentDescription = null,
            tint = if (device.state.isOn == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 8.dp)
        )
        Text(device.name, style = MaterialTheme.typography.titleSmall)
    }
}
