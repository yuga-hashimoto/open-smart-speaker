package com.opensmarthome.speaker.ui.settings.skills

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
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.opensmarthome.speaker.assistant.skills.SkillRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkillsScreen(
    onBack: () -> Unit,
    viewModel: SkillsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    (state as? SkillsViewModel.UiState.Loaded)?.errorMessage?.let { msg ->
        LaunchedEffect(msg) {
            snackbarHost.showSnackbar(msg)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Skills") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Back") }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) }
    ) { padding ->
        when (val current = state) {
            is SkillsViewModel.UiState.Loading ->
                LoadingContent(padding)

            is SkillsViewModel.UiState.Loaded ->
                LoadedContent(
                    loaded = current,
                    onInstall = { url -> viewModel.installFromUrl(url) },
                    onDelete = { name -> viewModel.delete(name) },
                    padding = padding
                )
        }
    }
}

@Composable
private fun LoadingContent(padding: PaddingValues) {
    Column(
        modifier = Modifier.fillMaxSize().padding(padding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun LoadedContent(
    loaded: SkillsViewModel.UiState.Loaded,
    onInstall: (String) -> Unit,
    onDelete: (String) -> Unit,
    padding: PaddingValues
) {
    var urlInput by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(16.dp)
    ) {
        item {
            InstallFromUrlCard(
                url = urlInput,
                onUrlChange = { urlInput = it },
                installing = loaded.installing,
                onInstallClick = {
                    onInstall(urlInput)
                    urlInput = ""
                }
            )
        }
        item {
            Text(
                text = "${loaded.skills.size} skills installed",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        items(loaded.skills, key = { it.name }) { skill ->
            SkillRow(skill = skill, onDelete = { onDelete(skill.name) })
        }
    }
}

@Composable
private fun InstallFromUrlCard(
    url: String,
    onUrlChange: (String) -> Unit,
    installing: Boolean,
    onInstallClick: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "Install skill from URL",
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(Modifier.size(8.dp))
            OutlinedTextField(
                value = url,
                onValueChange = onUrlChange,
                label = { Text("https://.../SKILL.md") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.size(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = onInstallClick,
                    enabled = !installing && url.isNotBlank()
                ) {
                    if (installing) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.size(8.dp))
                        Text("Installing…")
                    } else {
                        Text("Install")
                    }
                }
            }
        }
    }
}

@Composable
private fun SkillRow(
    skill: SkillRepository.SkillView,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = skill.name, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.size(4.dp))
            Text(
                text = skill.description,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.size(4.dp))
            Text(
                text = "Source: ${skill.source}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (skill.deletable) {
                HorizontalDivider(modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDelete) { Text("Remove") }
                }
            }
        }
    }
}
