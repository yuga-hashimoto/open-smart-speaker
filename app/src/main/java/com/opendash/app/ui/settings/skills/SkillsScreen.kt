package com.opendash.app.ui.settings.skills

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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.opendash.app.assistant.skills.SkillRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkillsScreen(
    onBack: () -> Unit,
    viewModel: SkillsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }

    val resolvedError = (state as? SkillsViewModel.UiState.Loaded)?.errorMessage?.let { msg ->
        stringResource(msg.resId, *msg.args.toTypedArray())
    }
    resolvedError?.let { text ->
        LaunchedEffect(text) {
            snackbarHost.showSnackbar(text)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.skills_title)) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text(stringResource(R.string.common_back)) }
                },
                actions = {
                    TextButton(onClick = { viewModel.reloadFromDisk() }) {
                        Text(stringResource(R.string.skills_reload))
                    }
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
                    onToggleEnabled = { name, enabled -> viewModel.setEnabled(name, enabled) },
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
    onToggleEnabled: (String, Boolean) -> Unit,
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
                text = stringResource(R.string.skills_installed_count, loaded.skills.size),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        items(loaded.skills, key = { it.name }) { skill ->
            SkillRow(
                skill = skill,
                onDelete = { onDelete(skill.name) },
                onToggleEnabled = { enabled -> onToggleEnabled(skill.name, enabled) }
            )
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
                stringResource(R.string.skills_install_from_url),
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(Modifier.size(8.dp))
            OutlinedTextField(
                value = url,
                onValueChange = onUrlChange,
                label = { Text(stringResource(R.string.skills_install_url_placeholder)) },
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
                        Text(stringResource(R.string.skills_installing))
                    } else {
                        Text(stringResource(R.string.skills_install_button))
                    }
                }
            }
        }
    }
}

@Composable
private fun SkillRow(
    skill: SkillRepository.SkillView,
    onDelete: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = skill.name,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = skill.enabled,
                    onCheckedChange = onToggleEnabled
                )
            }
            Spacer(Modifier.size(4.dp))
            Text(
                text = skill.description,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.size(4.dp))
            Text(
                text = stringResource(R.string.skills_source_prefix, skill.source),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (skill.deletable) {
                HorizontalDivider(modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDelete) { Text(stringResource(R.string.skills_remove)) }
                }
            }
        }
    }
}
