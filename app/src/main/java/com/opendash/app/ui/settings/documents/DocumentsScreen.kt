package com.opendash.app.ui.settings.documents

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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.opendash.app.R
import com.opendash.app.tool.rag.RagRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentsScreen(
    onBack: () -> Unit,
    viewModel: DocumentsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }

    val resolvedMessage = state.message?.let { msg ->
        stringResource(msg.resId, *msg.args.toTypedArray())
    }
    resolvedMessage?.let { text ->
        LaunchedEffect(text) {
            snackbarHost.showSnackbar(text)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.documents_title)) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text(stringResource(R.string.common_back)) }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) }
    ) { padding ->
        LoadedContent(
            state = state,
            onIngest = viewModel::ingest,
            onDelete = viewModel::delete,
            padding = padding
        )
    }
}

@Composable
private fun LoadedContent(
    state: DocumentsViewModel.UiState,
    onIngest: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    padding: PaddingValues
) {
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(16.dp)
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        stringResource(R.string.documents_teach_title),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(Modifier.size(8.dp))
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text(stringResource(R.string.documents_title_field)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.size(8.dp))
                    OutlinedTextField(
                        value = content,
                        onValueChange = { content = it },
                        label = { Text(stringResource(R.string.documents_content_field)) },
                        minLines = 5,
                        maxLines = 20,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.size(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(
                            onClick = {
                                onIngest(title, content)
                                title = ""
                                content = ""
                            },
                            enabled = !state.ingesting && title.isNotBlank() && content.isNotBlank()
                        ) {
                            if (state.ingesting) {
                                CircularProgressIndicator(
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.size(8.dp))
                                Text(stringResource(R.string.documents_ingesting))
                            } else {
                                Text(stringResource(R.string.documents_ingest_button))
                            }
                        }
                    }
                }
            }
        }
        item {
            Text(
                text = if (state.documents.isEmpty()) stringResource(R.string.documents_empty)
                else stringResource(R.string.documents_stored_count, state.documents.size),
                style = MaterialTheme.typography.labelMedium
            )
        }
        items(state.documents, key = { it.id }) { doc ->
            DocumentRow(doc = doc, onDelete = { onDelete(doc.id) })
        }
    }
}

@Composable
private fun DocumentRow(
    doc: RagRepository.DocumentSummary,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(doc.title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.size(4.dp))
            Text(
                text = stringResource(R.string.documents_chunks_count, doc.chunkCount),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HorizontalDivider(modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDelete) { Text(stringResource(R.string.common_delete)) }
            }
        }
    }
}
