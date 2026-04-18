package com.opendash.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.opendash.app.device.model.DeviceType
import com.opendash.app.ui.theme.DeviceClimate
import com.opendash.app.ui.theme.DeviceLightOn
import com.opendash.app.ui.theme.DeviceMediaPlaying
import com.opendash.app.ui.theme.SpeakerPrimary
import com.opendash.app.ui.theme.SpeakerSurfaceElevated
import com.opendash.app.ui.theme.SpeakerTextPrimary
import com.opendash.app.ui.theme.SpeakerTextSecondary

data class DeviceChip(
    val name: String,
    val type: DeviceType,
    val isOn: Boolean,
    val summary: String
)

@Composable
fun DeviceStatusChips(chips: List<DeviceChip>, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        chips.take(4).forEach { chip ->
            DeviceChipItem(chip)
        }
    }
}

@Composable
private fun DeviceChipItem(chip: DeviceChip) {
    val iconColor = when (chip.type) {
        DeviceType.LIGHT -> if (chip.isOn) DeviceLightOn else SpeakerTextSecondary
        DeviceType.CLIMATE -> DeviceClimate
        DeviceType.MEDIA_PLAYER -> if (chip.isOn) DeviceMediaPlaying else SpeakerTextSecondary
        else -> if (chip.isOn) SpeakerPrimary else SpeakerTextSecondary
    }
    val icon = when (chip.type) {
        DeviceType.LIGHT -> Icons.Filled.Lightbulb
        DeviceType.CLIMATE -> Icons.Filled.Thermostat
        DeviceType.MEDIA_PLAYER -> Icons.Filled.PlayArrow
        else -> Icons.Filled.Power
    }

    Row(
        modifier = Modifier
            .background(SpeakerSurfaceElevated, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(18.dp))
        Text(chip.name, style = MaterialTheme.typography.bodySmall, color = SpeakerTextPrimary, maxLines = 1)
    }
}
