package com.opendash.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import com.opendash.app.ui.theme.SpeakerSurfaceVariant
import com.opendash.app.ui.theme.SpeakerTextPrimary
import com.opendash.app.ui.theme.SpeakerTextSecondary
import com.opendash.app.util.ThermalLevel

/**
 * Persistent tablet-self status strip: thermal throttle badge.
 * Shown top-right of the Home dashboard. Battery chip was removed
 * because it overlapped with the settings gear icon — the system
 * status bar already shows charge level on every Android tablet.
 *
 * Thermal chip only renders when the device is actually throttling;
 * keeping it hidden in the common-case NORMAL state avoids a permanent
 * "everything is fine!" chip that would dilute the signal when it matters.
 */
@Composable
fun TabletStatusChips(
    thermal: ThermalLevel,
    modifier: Modifier = Modifier,
) {
    if (!thermal.shouldThrottle) return
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ThermalChip(thermal)
    }
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
