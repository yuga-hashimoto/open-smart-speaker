package com.opendash.app.ui.settings.memory

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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.opendash.app.R
import com.opendash.app.data.db.MemoryEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryScreen(
    onBack: () -> Unit,
    viewModel: MemoryViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showClearDialog by remember { mutableStateOf(false) }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringResource(R.string.memory_clear_dialog_title)) },
            text = { Text(stringResource(R.string.memory_clear_dialog_body)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearAll()
                    showClearDialog = false
                }) { Text(stringResource(R.string.memory_clear_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.memory_title)) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text(stringResource(R.string.common_back)) }
                },
                actions = {
                    TextButton(
                        onClick = { showClearDialog = true },
                        enabled = state.entries.isNotEmpty()
                    ) { Text(stringResource(R.string.memory_clear_all)) }
                }
            )
        }
    ) { padding ->
        if (state.loading) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LoadedContent(
                state = state,
                onQueryChange = viewModel::updateQuery,
                onDelete = viewModel::delete,
                padding = padding
            )
        }
    }
}

@Composable
private fun LoadedContent(
    state: MemoryViewModel.UiState,
    onQueryChange: (String) -> Unit,
    onDelete: (String) -> Unit,
    padding: PaddingValues
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(16.dp)
    ) {
        item {
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChange,
                label = { Text(stringResource(R.string.memory_search_placeholder)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            Text(
                text = if (state.entries.isEmpty()) stringResource(R.string.memory_empty)
                else stringResource(R.string.memory_count, state.entries.size),
                style = MaterialTheme.typography.labelMedium
            )
        }
        items(state.entries, key = { it.key }) { entry ->
            MemoryRow(entry = entry, onDelete = { onDelete(entry.key) })
        }
    }
}

@Composable
private fun MemoryRow(
    entry: MemoryEntity,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(entry.key, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.size(4.dp))
            Text(entry.value, style = MaterialTheme.typography.bodyMedium)
            HorizontalDivider(modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDelete) { Text(stringResource(R.string.memory_forget)) }
            }
        }
    }
}
