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
import com.opendash.app.homeassistant.model.Entity

@Composable
fun EntityCard(
    entity: Entity,
    onToggle: (Entity) -> Unit,
    onBrightnessChange: (Entity, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth().padding(4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        when (entity.domain) {
            "light" -> LightCard(entity, onToggle, onBrightnessChange)
            "switch", "input_boolean" -> SwitchCard(entity, onToggle)
            "climate" -> ClimateCard(entity)
            "media_player" -> MediaPlayerCard(entity)
            else -> GenericCard(entity)
        }
    }
}

@Composable
private fun LightCard(
    entity: Entity,
    onToggle: (Entity) -> Unit,
    onBrightnessChange: (Entity, Float) -> Unit
) {
    Column(modifier = Modifier.padding(12.dp)) {
        EntityHeader(entity, Icons.Filled.Lightbulb)
        Switch(
            checked = entity.state == "on",
            onCheckedChange = { onToggle(entity) }
        )
        val brightness = (entity.attributes["brightness"] as? Number)?.toFloat()
        if (brightness != null && entity.state == "on") {
            Slider(
                value = brightness / 255f,
                onValueChange = { onBrightnessChange(entity, it * 255f) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun SwitchCard(entity: Entity, onToggle: (Entity) -> Unit) {
    Column(modifier = Modifier.padding(12.dp)) {
        EntityHeader(entity, Icons.Filled.Power)
        Switch(
            checked = entity.state == "on",
            onCheckedChange = { onToggle(entity) }
        )
    }
}

@Composable
private fun ClimateCard(entity: Entity) {
    Column(modifier = Modifier.padding(12.dp)) {
        EntityHeader(entity, Icons.Filled.Thermostat)
        val temp = (entity.attributes["current_temperature"] as? Number)?.toFloat()
        val target = (entity.attributes["temperature"] as? Number)?.toFloat()
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("Current: ${temp ?: "?"}°", style = MaterialTheme.typography.bodyMedium)
            Text("Target: ${target ?: "?"}°", style = MaterialTheme.typography.bodyMedium)
        }
        Text(
            text = entity.state.replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.secondary
        )
    }
}

@Composable
private fun MediaPlayerCard(entity: Entity) {
    Column(modifier = Modifier.padding(12.dp)) {
        EntityHeader(entity, Icons.Filled.PlayArrow)
        val mediaTitle = entity.attributes["media_title"] as? String
        if (mediaTitle != null) {
            Text(mediaTitle, style = MaterialTheme.typography.bodySmall)
        }
        Text(
            text = entity.state.replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.secondary
        )
    }
}

@Composable
private fun GenericCard(entity: Entity) {
    Column(modifier = Modifier.padding(12.dp)) {
        Text(entity.friendlyName, style = MaterialTheme.typography.titleSmall)
        Text(entity.state, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun EntityHeader(entity: Entity, icon: ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(bottom = 4.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (entity.state == "on") MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 8.dp)
        )
        Text(entity.friendlyName, style = MaterialTheme.typography.titleSmall)
    }
}
