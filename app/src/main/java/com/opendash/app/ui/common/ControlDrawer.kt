package com.opendash.app.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.opendash.app.ui.theme.SpeakerPrimary
import com.opendash.app.ui.theme.SpeakerSurface
import com.opendash.app.ui.theme.SpeakerSurfaceElevated
import com.opendash.app.ui.theme.SpeakerTextPrimary
import com.opendash.app.ui.theme.SpeakerTextSecondary

@Composable
fun ControlDrawer(
    visible: Boolean,
    onNightMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    var brightness by remember { mutableFloatStateOf(0.7f) }
    var volume by remember { mutableFloatStateOf(0.5f) }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(tween(300)) { -it },
        exit = slideOutVertically(tween(300)) { -it },
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(SpeakerSurface, RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp))
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            // Brightness
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Brightness6, contentDescription = null, tint = SpeakerTextSecondary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Text("Brightness", style = MaterialTheme.typography.bodySmall, color = SpeakerTextSecondary, modifier = Modifier.width(80.dp))
                Slider(
                    value = brightness,
                    onValueChange = { brightness = it },
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(thumbColor = SpeakerPrimary, activeTrackColor = SpeakerPrimary)
                )
            }

            Spacer(Modifier.height(8.dp))

            // Volume
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.VolumeUp, contentDescription = null, tint = SpeakerTextSecondary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Text("Volume", style = MaterialTheme.typography.bodySmall, color = SpeakerTextSecondary, modifier = Modifier.width(80.dp))
                Slider(
                    value = volume,
                    onValueChange = { volume = it },
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(thumbColor = SpeakerPrimary, activeTrackColor = SpeakerPrimary)
                )
            }

            Spacer(Modifier.height(8.dp))

            // Night mode button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = onNightMode) {
                    Icon(Icons.Filled.DarkMode, contentDescription = "Night Clock", tint = SpeakerTextPrimary)
                }
            }
        }
    }
}
