package com.opendash.app.ui.devices

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.opendash.app.device.model.Device
import com.opendash.app.device.model.DeviceType
import com.opendash.app.ui.theme.DeviceClimate
import com.opendash.app.ui.theme.DeviceLightOn
import com.opendash.app.ui.theme.DeviceLock
import com.opendash.app.ui.theme.DeviceMediaPlaying
import com.opendash.app.ui.theme.SpeakerPrimary
import com.opendash.app.ui.theme.SpeakerSurface
import com.opendash.app.ui.theme.SpeakerSurfaceElevated
import com.opendash.app.ui.theme.SpeakerTextPrimary
import com.opendash.app.ui.theme.SpeakerTextSecondary
import com.opendash.app.ui.theme.SpeakerTextTertiary

@Composable
fun RoomDeviceCard(
    device: Device,
    onToggle: (Device) -> Unit,
    modifier: Modifier = Modifier
) {
    val isOn = device.state.isOn == true
    val iconColor = when (device.type) {
        DeviceType.LIGHT -> if (isOn) DeviceLightOn else SpeakerTextTertiary
        DeviceType.CLIMATE -> DeviceClimate
        DeviceType.MEDIA_PLAYER -> if (isOn) DeviceMediaPlaying else SpeakerTextTertiary
        DeviceType.LOCK -> DeviceLock
        else -> if (isOn) SpeakerPrimary else SpeakerTextTertiary
    }
    val icon = when (device.type) {
        DeviceType.LIGHT -> Icons.Filled.Lightbulb
        DeviceType.CLIMATE -> Icons.Filled.Thermostat
        DeviceType.MEDIA_PLAYER -> Icons.Filled.PlayArrow
        DeviceType.LOCK -> Icons.Filled.Lock
        else -> Icons.Filled.Power
    }

    Surface(
        modifier = modifier.height(100.dp).clickable { onToggle(device) },
        shape = RoundedCornerShape(16.dp),
        color = if (isOn) SpeakerSurfaceElevated else SpeakerSurface
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(24.dp))
            }
            Column {
                Text(device.name, style = MaterialTheme.typography.bodyLarge, color = SpeakerTextPrimary, maxLines = 1)
                Text(
                    text = when (device.type) {
                        DeviceType.LIGHT -> if (isOn) device.state.brightness?.let { "${(it / 255 * 100).toInt()}%" } ?: "On" else "Off"
                        DeviceType.CLIMATE -> device.state.temperature?.let { "%.1f\u00B0".format(it) } ?: ""
                        DeviceType.MEDIA_PLAYER -> device.state.mediaTitle ?: if (isOn) "Playing" else "Off"
                        else -> if (isOn) "On" else "Off"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = SpeakerTextSecondary
                )
            }
        }
    }
}
