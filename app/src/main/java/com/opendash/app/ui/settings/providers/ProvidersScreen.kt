package com.opendash.app.ui.settings.providers

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
import androidx.annotation.StringRes
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.opendash.app.R

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
                title = { Text(stringResource(R.string.providers_title)) },
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.common_back)) } }
            )
        }
    ) { padding ->
        if (rows.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    stringResource(R.string.providers_empty),
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
                        text = stringResource(R.string.providers_active),
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
                if (row.isLocal) Badge(stringResource(R.string.providers_badge_on_device))
                if (row.supportsStreaming) Badge(stringResource(R.string.providers_badge_streaming))
                if (row.supportsTools) Badge(stringResource(R.string.providers_badge_tools))
                if (row.supportsVision) Badge(stringResource(R.string.providers_badge_vision))
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
    val broadcastStatus = stringResource(
        if (state.broadcastEnabled) R.string.multiroom_status_on else R.string.multiroom_status_off
    )
    val secretStatus = stringResource(
        if (state.hasSecret) R.string.multiroom_secret_set else R.string.multiroom_secret_not_set
    )
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(R.string.multiroom_title), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.size(8.dp))
            Text(
                text = stringResource(R.string.multiroom_broadcast, broadcastStatus),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = stringResource(R.string.multiroom_shared_secret, secretStatus),
                style = MaterialTheme.typography.bodyMedium
            )
            if (state.broadcastEnabled && state.broadcastingAs != null) {
                Text(
                    text = stringResource(R.string.multiroom_broadcasting_as, state.broadcastingAs),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = stringResource(
                    R.string.multiroom_peers_summary,
                    state.peerCount, state.freshCount, state.staleCount, state.goneCount
                ),
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
            text = stringResource(hint.messageRes),
            style = MaterialTheme.typography.bodyMedium,
            color = if (hint.healthy) hint.color else MaterialTheme.colorScheme.onSurface
        )
    }
}

internal data class MeshHealthHintText(
    val healthy: Boolean,
    val icon: String,
    val color: Color,
    @StringRes val messageRes: Int,
)

/**
 * Resolve the single most actionable hint. Order matters: fix broadcast first,
 * then secret, then peers — a missing earlier step makes later steps moot.
 * Exposed internal for unit testing.
 */
internal fun meshHealthHint(state: ProvidersViewModel.MultiroomState): MeshHealthHintText {
    val green = Color(0xFF2E7D32)
    val amber = Color(0xFFB26A00)
    return when {
        !state.broadcastEnabled -> MeshHealthHintText(
            healthy = false,
            icon = "!",
            color = amber,
            messageRes = R.string.multiroom_hint_broadcast_off,
        )
        !state.hasSecret -> MeshHealthHintText(
            healthy = false,
            icon = "!",
            color = amber,
            messageRes = R.string.multiroom_hint_no_secret,
        )
        state.freshCount == 0 -> MeshHealthHintText(
            healthy = false,
            icon = "!",
            color = amber,
            messageRes = R.string.multiroom_hint_no_peers,
        )
        else -> MeshHealthHintText(
            healthy = true,
            icon = "\u2713",
            color = green,
            messageRes = R.string.multiroom_hint_healthy,
        )
    }
}
