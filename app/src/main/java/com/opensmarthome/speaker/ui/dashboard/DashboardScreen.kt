package com.opensmarthome.speaker.ui.dashboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun DashboardScreen(
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val grouped by viewModel.groupedEntities.collectAsState()
    val isConnected by viewModel.isConnected.collectAsState()

    Column(modifier = modifier.fillMaxSize()) {
        QuickActionRow(
            actions = viewModel.quickActions,
            onAction = { viewModel.executeQuickAction(it) }
        )

        if (!isConnected) {
            Text(
                text = "Not connected to Home Assistant",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 180.dp),
            modifier = Modifier.fillMaxSize().padding(8.dp)
        ) {
            grouped.forEach { (domain, entities) ->
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = domain.replaceFirstChar { it.uppercase() }.replace("_", " "),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
                    )
                }
                items(entities, key = { it.entityId }) { entity ->
                    EntityCard(
                        entity = entity,
                        onToggle = { viewModel.toggleEntity(it) },
                        onBrightnessChange = { e, b -> viewModel.setBrightness(e, b) }
                    )
                }
            }
        }
    }
}
