package com.opendash.app.ui.settings.sysinfo

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.opendash.app.R

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
                title = { Text(stringResource(R.string.sysinfo_title)) },
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.common_back)) } },
                actions = {
                    TextButton(onClick = { viewModel.refresh() }) { Text(stringResource(R.string.common_refresh)) }
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
        val modelNone = stringResource(R.string.sysinfo_model_none)
        val notBroadcasting = stringResource(R.string.sysinfo_not_broadcasting)
        val online = stringResource(R.string.sysinfo_online)
        val offline = stringResource(R.string.sysinfo_offline)
        val freshLabel = stringResource(R.string.sysinfo_freshness_fresh)
        val staleLabel = stringResource(R.string.sysinfo_freshness_stale)
        val goneLabel = stringResource(R.string.sysinfo_freshness_gone)
        val rows = buildList {
            add(stringResource(R.string.sysinfo_active_model) to (state.activeProviderModel ?: modelNone))
            add(stringResource(R.string.sysinfo_providers_count) to "${state.providerCount}")
            add(stringResource(R.string.sysinfo_tools_count) to "${state.toolCount}")
            add(stringResource(R.string.sysinfo_devices_count) to "${state.deviceCount}")
            state.devicesByType.forEach { (type, count) ->
                add("  • $type" to "$count")
            }
            add(stringResource(R.string.sysinfo_skills_count) to "${state.skillCount}")
            add(stringResource(R.string.sysinfo_routines_count) to "${state.routineCount}")
            add(stringResource(R.string.sysinfo_documents_count) to "${state.documentCount}")
            add(stringResource(R.string.sysinfo_memory_count) to "${state.memoryCount}")
            add(stringResource(R.string.sysinfo_connectivity) to if (state.online) online else offline)
            add(stringResource(R.string.sysinfo_latency_violations) to "${state.totalBudgetViolations}")
            add(stringResource(R.string.sysinfo_latency_measurements) to "${state.totalLatencyMeasurements}")
            add(stringResource(R.string.sysinfo_thermal_state) to state.thermalLevel)
            add(stringResource(R.string.sysinfo_broadcasting_as) to (registeredName ?: notBroadcasting))
            add(stringResource(R.string.sysinfo_nearby_speakers) to if (nearby.isEmpty()) modelNone else "${nearby.size}")
            nearby.forEach { speaker ->
                val hostSuffix = speaker.host?.let { host ->
                    speaker.port?.let { " — $host:$it" } ?: " — $host"
                } ?: ""
                val freshnessSuffix = when (freshness[speaker.serviceName]) {
                    com.opendash.app.multiroom.PeerFreshness.Fresh -> " · $freshLabel"
                    com.opendash.app.multiroom.PeerFreshness.Stale -> " · $staleLabel"
                    com.opendash.app.multiroom.PeerFreshness.Gone -> " · $goneLabel"
                    null -> ""
                }
                val value = (hostSuffix + freshnessSuffix).trimStart(' ', '—', ' ')
                add("  • ${speaker.serviceName}" to value)
            }
            if (state.multiroomTraffic.isNotEmpty()) {
                add(stringResource(R.string.sysinfo_multiroom_traffic) to "")
                state.multiroomTraffic.forEach { row ->
                    add(
                        "  • ${row.type}" to
                            stringResource(R.string.sysinfo_traffic_summary, row.outbound, row.inbound)
                    )
                }
            }
            if (state.rejections.isNotEmpty()) {
                add(stringResource(R.string.sysinfo_multiroom_rejections) to "")
                state.rejections.forEach { row ->
                    val hintText = row.hintRes?.let { stringResource(it) } ?: row.hintFallback
                    add("  • ${row.reason}" to "${row.count}× $hintText")
                }
            }
        }
        val showClearCounters = state.multiroomTraffic.isNotEmpty() || state.rejections.isNotEmpty()
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(rows, key = { it.first }) { row ->
                InfoRow(label = row.first, value = row.second)
            }
            if (showClearCounters) {
                item(key = "clear-multiroom-counters") {
                    TextButton(
                        onClick = { viewModel.clearMultiroomCounters() },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.sysinfo_clear_multiroom)) }
                }
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
