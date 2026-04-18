package com.opendash.app.ui.settings.prompt

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.opendash.app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemPromptScreen(
    onBack: () -> Unit,
    viewModel: SystemPromptViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()

    // Reset saved flag on any edit
    LaunchedEffect(state.saved) {
        // no-op — flag is reset by updatePrompt
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.system_prompt_title)) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text(stringResource(R.string.common_back)) }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(scrollState)
        ) {
            Text(
                text = stringResource(R.string.system_prompt_description),
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.size(12.dp))

            OutlinedTextField(
                value = state.prompt,
                onValueChange = viewModel::updatePrompt,
                label = {
                    Text(
                        stringResource(
                            if (state.usingDefault) R.string.system_prompt_label_default
                            else R.string.system_prompt_label_custom
                        )
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .size(width = 10.dp, height = 300.dp)
                    .fillMaxWidth(),
                minLines = 10,
                maxLines = 20
            )

            Spacer(Modifier.size(8.dp))

            if (state.saved) {
                Text(
                    text = stringResource(R.string.system_prompt_saved_message),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.size(8.dp))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { viewModel.save() }) {
                    Text(stringResource(R.string.common_save))
                }
                OutlinedButton(onClick = { viewModel.resetToDefault() }) {
                    Text(stringResource(R.string.system_prompt_reset))
                }
            }
        }
    }
}
