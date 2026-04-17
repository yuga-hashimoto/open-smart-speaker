package com.opensmarthome.speaker.ui.settings.sysinfo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemInfoScreen(
    onBack: () -> Unit,
    viewModel: SystemInfoViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val nearby by viewModel.nearbySpeakers.collectAsStateWithLifecycle()
    val registeredName by viewModel.registeredName.collectAsStateWithLifecycle()
    val freshness by viewModel.peerFreshness.collectAsStateWithLifecycle()

    DisposableEffect(Unit) {
        viewModel.startDiscovery()
        onDispose { viewModel.stopDiscovery() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("System info") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
                actions = {
                    TextButton(onClick = { viewModel.refresh() }) { Text("Refresh") }
                }
            )
        }
    ) { padding ->
        if (state.loading) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) { CircularProgressIndicator() }
            return@Scaffold
        }
        val rows = buildList {
            add("Active model" to (state.activeProviderModel ?: "(none)"))
            add("Providers registered" to "${state.providerCount}")
            add("Tools available" to "${state.toolCount}")
            add("Smart-home devices" to "${state.deviceCount}")
            state.devicesByType.forEach { (type, count) ->
                add("  • $type" to "$count")
            }
            add("Skills" to "${state.skillCount}")
            add("Routines" to "${state.routineCount}")
            add("Documents (RAG)" to "${state.documentCount}")
            add("Memory entries" to "${state.memoryCount}")
            add("Connectivity" to if (state.online) "Online" else "Offline")
            add("Latency budget violations" to "${state.totalBudgetViolations}")
            add("Latency measurements (lifetime)" to "${state.totalLatencyMeasurements}")
            add("Thermal state" to state.thermalLevel)
            add("Broadcasting as" to (registeredName ?: "(not broadcasting)"))
            add("Nearby speakers (mDNS)" to if (nearby.isEmpty()) "(none)" else "${nearby.size}")
            nearby.forEach { speaker ->
                val hostSuffix = speaker.host?.let { host ->
                    speaker.port?.let { " — $host:$it" } ?: " — $host"
                } ?: ""
                val freshnessSuffix = when (freshness[speaker.serviceName]) {
                    com.opensmarthome.speaker.multiroom.PeerFreshness.Fresh -> " · fresh"
                    com.opensmarthome.speaker.multiroom.PeerFreshness.Stale -> " · stale"
                    com.opensmarthome.speaker.multiroom.PeerFreshness.Gone -> " · gone"
                    null -> ""
                }
                val value = (hostSuffix + freshnessSuffix).trimStart(' ', '—', ' ')
                add("  • ${speaker.serviceName}" to value)
            }
            if (state.multiroomTraffic.isNotEmpty()) {
                // Collapsed `{in}/{out}` counter per envelope type — lets the
                // user sanity-check that the mesh is actually exchanging
                // traffic without paging through logs.
                add("Multi-room traffic" to "")
                state.multiroomTraffic.forEach { row ->
                    add("  • ${row.type}" to "${row.outbound} out · ${row.inbound} in")
                }
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(rows, key = { it.first }) { row ->
                InfoRow(label = row.first, value = row.second)
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium)
        }
    }
}
