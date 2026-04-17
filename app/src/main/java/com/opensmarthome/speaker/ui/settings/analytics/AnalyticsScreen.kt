package com.opensmarthome.speaker.ui.settings.analytics

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.opensmarthome.speaker.data.db.ToolUsageEntity
import com.opensmarthome.speaker.tool.analytics.AnalyticsRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    onBack: () -> Unit,
    viewModel: AnalyticsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showReset by remember { mutableStateOf(false) }

    if (showReset) {
        AlertDialog(
            onDismissRequest = { showReset = false },
            title = { Text("Reset usage stats?") },
            text = { Text("Clears all tool usage history. This is local data only.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.reset()
                    showReset = false
                }) { Text("Reset") }
            },
            dismissButton = {
                TextButton(onClick = { showReset = false }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Usage") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Back") }
                },
                actions = {
                    TextButton(
                        onClick = { showReset = true },
                        enabled = (state.summary?.totalInvocations ?: 0) > 0
                    ) { Text("Reset") }
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
        } else {
            LoadedContent(
                summary = state.summary,
                allTime = state.allTime,
                latency = state.latency,
                fastPathRate = state.fastPathRate,
                padding = padding,
                onClearToolUsageStats = viewModel::clearToolUsageStats
            )
        }
    }
}

@Composable
private fun LoadedContent(
    summary: AnalyticsRepository.Summary?,
    allTime: List<ToolUsageEntity>,
    latency: List<AnalyticsViewModel.LatencyRow>,
    fastPathRate: Double?,
    padding: PaddingValues,
    onClearToolUsageStats: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(16.dp)
    ) {
        summary?.let { item { SummaryCard(it) } }
        fastPathRate?.let { rate ->
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "${(rate * 100).toInt()}% fast-path",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Spacer(Modifier.size(4.dp))
                        Text(
                            text = "Canonical commands handled without the LLM — higher = snappier UX.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (latency.isNotEmpty()) {
            item {
                Text(
                    text = "Voice pipeline latency",
                    style = MaterialTheme.typography.labelMedium
                )
            }
            items(latency, key = { it.event }) { row ->
                LatencyRowCard(row = row)
            }
        }

        item {
            Text(
                text = if (allTime.isEmpty()) "No tools used yet."
                else "All-time usage (${allTime.size} tools)",
                style = MaterialTheme.typography.labelMedium
            )
        }
        items(allTime, key = { it.toolName }) { entry ->
            ToolUsageRow(entry = entry)
        }

        if (allTime.isNotEmpty()) {
            item(key = "clear-tool-usage-stats") {
                TextButton(
                    onClick = onClearToolUsageStats,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Clear tool usage stats") }
            }
        }
    }
}

@Composable
private fun LatencyRowCard(row: AnalyticsViewModel.LatencyRow) {
    val overBudget = row.violations > 0
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(row.event, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = if (overBudget) "${row.violations} over" else "within budget",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (overBudget) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.size(4.dp))
            Text(
                text = "avg ${row.averageMs}ms • p95 ${row.p95Ms}ms" +
                    if (row.budgetMs > 0) " • budget ${row.budgetMs}ms" else "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SummaryCard(summary: AnalyticsRepository.Summary) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "${summary.totalInvocations} tool calls",
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(Modifier.size(4.dp))
            Text(
                text = "${(summary.globalSuccessRate * 100).toInt()}% success • " +
                    "${summary.uniqueTools} unique tools",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun ToolUsageRow(entry: ToolUsageEntity) {
    val successRate = if (entry.totalCalls == 0L) 0 else (entry.successCalls * 100 / entry.totalCalls)
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(entry.toolName, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "$successRate% success",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "${entry.totalCalls}",
                style = MaterialTheme.typography.titleLarge
            )
        }
    }
}
