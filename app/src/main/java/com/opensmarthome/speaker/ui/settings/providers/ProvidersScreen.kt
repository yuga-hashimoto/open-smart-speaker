package com.opensmarthome.speaker.ui.settings.providers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProvidersScreen(
    onBack: () -> Unit,
    viewModel: ProvidersViewModel = hiltViewModel()
) {
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    val multiroom by viewModel.multiroomState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Assistant providers") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }
            )
        }
    ) { padding ->
        if (rows.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "No providers registered yet. Download an on-device model or configure a gateway in Settings.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.size(16.dp))
                MultiroomCard(state = multiroom)
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(16.dp)
        ) {
            items(rows, key = { it.id }) { row ->
                ProviderRow(row = row, onSelect = { viewModel.select(row.id) })
            }
            item(key = "__multiroom__") {
                Spacer(Modifier.size(8.dp))
                MultiroomCard(state = multiroom)
            }
        }
    }
}

@Composable
private fun ProviderRow(
    row: ProvidersViewModel.Row,
    onSelect: () -> Unit
) {
    Card(
        onClick = onSelect,
        modifier = Modifier.fillMaxWidth(),
        colors = if (row.isActive) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else CardDefaults.cardColors()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(row.displayName, style = MaterialTheme.typography.titleMedium)
                if (row.isActive) {
                    Text(
                        text = "Active",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(Modifier.size(4.dp))
            Text(
                text = row.modelName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.size(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (row.isLocal) Badge("On-device")
                if (row.supportsStreaming) Badge("Streaming")
                if (row.supportsTools) Badge("Tools")
                if (row.supportsVision) Badge("Vision")
            }
        }
    }
}

@Composable
private fun Badge(label: String) {
    AssistChip(
        onClick = {},
        label = { Text(label) },
        colors = AssistChipDefaults.assistChipColors()
    )
}

@Composable
private fun MultiroomCard(state: ProvidersViewModel.MultiroomState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Multi-room", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.size(8.dp))
            Text(
                text = "Broadcast: ${if (state.broadcastEnabled) "On" else "Off"}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Shared secret: ${if (state.hasSecret) "Set" else "Not set"}",
                style = MaterialTheme.typography.bodyMedium
            )
            if (state.broadcastEnabled && state.broadcastingAs != null) {
                Text(
                    text = "Broadcasting as: ${state.broadcastingAs}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "Peers: ${state.peerCount} " +
                    "(${state.freshCount} fresh, ${state.staleCount} stale, ${state.goneCount} gone)",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.size(8.dp))
            MeshHealthHint(state = state)
        }
    }
}

@Composable
private fun MeshHealthHint(state: ProvidersViewModel.MultiroomState) {
    val hint = meshHealthHint(state)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = hint.icon,
            color = hint.color,
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.size(6.dp))
        Text(
            text = hint.message,
            style = MaterialTheme.typography.bodyMedium,
            color = if (hint.healthy) hint.color else MaterialTheme.colorScheme.onSurface
        )
    }
}

internal data class MeshHealthHintText(
    val healthy: Boolean,
    val icon: String,
    val color: Color,
    val message: String
)

/**
 * Resolve the single most actionable hint. Order matters: fix broadcast first,
 * then secret, then peers — a missing earlier step makes later steps moot.
 * Exposed internal for unit testing.
 */
internal fun meshHealthHint(state: ProvidersViewModel.MultiroomState): MeshHealthHintText {
    val green = Color(0xFF2E7D32)
    return when {
        !state.broadcastEnabled -> MeshHealthHintText(
            healthy = false,
            icon = "!",
            color = Color(0xFFB26A00),
            message = "Turn on Multi-room broadcast to join the mesh."
        )
        !state.hasSecret -> MeshHealthHintText(
            healthy = false,
            icon = "!",
            color = Color(0xFFB26A00),
            message = "Set a shared secret"
        )
        state.freshCount == 0 -> MeshHealthHintText(
            healthy = false,
            icon = "!",
            color = Color(0xFFB26A00),
            message = "No peers yet — check that other tablets have Multi-room broadcast on."
        )
        else -> MeshHealthHintText(
            healthy = true,
            icon = "\u2713",
            color = green,
            message = "Mesh is healthy."
        )
    }
}
