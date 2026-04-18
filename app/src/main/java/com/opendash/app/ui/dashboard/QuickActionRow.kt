package com.opendash.app.ui.dashboard

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class QuickAction(
    val label: String,
    val domain: String,
    val service: String,
    val entityId: String? = null
)

@Composable
fun QuickActionRow(
    actions: List<QuickAction>,
    onAction: (QuickAction) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        actions.forEach { action ->
            FilledTonalButton(
                onClick = { onAction(action) },
                modifier = Modifier.padding(end = 8.dp)
            ) {
                val icon = when {
                    action.label.contains("off", ignoreCase = true) -> Icons.Filled.DarkMode
                    action.label.contains("on", ignoreCase = true) -> Icons.Filled.LightMode
                    else -> Icons.Filled.Movie
                }
                Icon(icon, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                Text(action.label)
            }
        }
    }
}
