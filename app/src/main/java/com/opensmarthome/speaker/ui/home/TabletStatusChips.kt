package com.opensmarthome.speaker.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Battery1Bar
import androidx.compose.material.icons.filled.Battery3Bar
import androidx.compose.material.icons.filled.Battery5Bar
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.DeviceThermostat
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.opensmarthome.speaker.ui.theme.SpeakerSurfaceVariant
import com.opensmarthome.speaker.ui.theme.SpeakerTextPrimary
import com.opensmarthome.speaker.ui.theme.SpeakerTextSecondary
import com.opensmarthome.speaker.util.BatteryStatus
import com.opensmarthome.speaker.util.ThermalLevel

/**
 * Persistent tablet-self status strip: battery level + charging state,
 * thermal throttle badge. Shown top-right of the Home dashboard so
 * users always see that the device is healthy (or not) — same vibe as
 * the status icons on an Echo Show's status bar.
 *
 * Thermal chip only renders when the device is actually throttling;
 * keeping it hidden in the common-case NORMAL state avoids a permanent
 * "everything is fine!" chip that would dilute the signal when it matters.
 */
@Composable
fun TabletStatusChips(
    battery: BatteryStatus,
    thermal: ThermalLevel,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BatteryChip(battery)
        if (thermal.shouldThrottle) {
            ThermalChip(thermal)
        }
    }
}

@Composable
private fun BatteryChip(battery: BatteryStatus) {
    val icon = when {
        battery.isCharging -> Icons.Filled.BatteryChargingFull
        battery.level >= 90 -> Icons.Filled.BatteryFull
        battery.level >= 60 -> Icons.Filled.Battery5Bar
        battery.level >= 30 -> Icons.Filled.Battery3Bar
        battery.level >= 10 -> Icons.Filled.Battery1Bar
        else -> Icons.Filled.BatteryStd
    }
    val tint = when {
        battery.isLow -> Color(0xFFE57373) // soft red
        battery.isCharging -> Color(0xFF81C784) // soft green
        else -> SpeakerTextSecondary
    }
    StatusChip(
        icon = icon,
        tint = tint,
        label = "${battery.level}%",
    )
}

@Composable
private fun ThermalChip(thermal: ThermalLevel) {
    val icon = when (thermal) {
        ThermalLevel.WARM -> Icons.Filled.WbSunny
        ThermalLevel.HOT -> Icons.Filled.LocalFireDepartment
        ThermalLevel.NORMAL -> Icons.Filled.DeviceThermostat
    }
    val tint = when (thermal) {
        ThermalLevel.HOT -> Color(0xFFEF5350)
        ThermalLevel.WARM -> Color(0xFFFFB74D)
        ThermalLevel.NORMAL -> SpeakerTextSecondary
    }
    val label = when (thermal) {
        ThermalLevel.WARM -> "Warm"
        ThermalLevel.HOT -> "Hot"
        ThermalLevel.NORMAL -> ""
    }
    StatusChip(icon = icon, tint = tint, label = label)
}

@Composable
private fun StatusChip(
    icon: ImageVector,
    tint: Color,
    label: String,
) {
    Row(
        modifier = Modifier
            .background(SpeakerSurfaceVariant, RoundedCornerShape(999.dp))
            .padding(PaddingValues(horizontal = 10.dp, vertical = 6.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(16.dp),
        )
        if (label.isNotBlank()) {
            Spacer(Modifier.size(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = SpeakerTextPrimary,
            )
        }
    }
}
