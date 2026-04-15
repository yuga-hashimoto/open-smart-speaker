package com.opensmarthome.speaker.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val haBaseUrl by viewModel.haBaseUrl.collectAsState()
    val haToken by viewModel.haToken.collectAsState()
    val openClawUrl by viewModel.openClawUrl.collectAsState()
    val localLlmUrl by viewModel.localLlmUrl.collectAsState()
    val localLlmModel by viewModel.localLlmModel.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        // Home Assistant Section
        SectionHeader("Home Assistant")
        SettingsTextField("Base URL", haBaseUrl) { url ->
            viewModel.saveHaSettings(url, haToken)
        }
        SettingsPasswordField("Long-Lived Access Token", haToken) { token ->
            viewModel.saveHaSettings(haBaseUrl, token)
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

        // OpenClaw Section
        SectionHeader("OpenClaw")
        SettingsTextField("Gateway URL", openClawUrl) { url ->
            viewModel.saveOpenClawSettings(url)
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

        // Local LLM Section
        SectionHeader("Local LLM (OpenAI Compatible)")
        SettingsTextField("Endpoint URL", localLlmUrl) { url ->
            viewModel.saveLocalLlmSettings(url, localLlmModel)
        }
        SettingsTextField("Model Name", localLlmModel) { model ->
            viewModel.saveLocalLlmSettings(localLlmUrl, model)
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

        // SwitchBot Section
        val switchBotToken by viewModel.switchBotToken.collectAsState()
        val switchBotSecret by viewModel.switchBotSecret.collectAsState()
        SectionHeader("SwitchBot")
        SettingsTextField("Token", switchBotToken) { token ->
            viewModel.saveSwitchBotSettings(token, switchBotSecret)
        }
        SettingsPasswordField("Secret Key", switchBotSecret) { secret ->
            viewModel.saveSwitchBotSettings(switchBotToken, secret)
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

        // MQTT Section
        val mqttBrokerUrl by viewModel.mqttBrokerUrl.collectAsState()
        SectionHeader("MQTT (Shelly / Tasmota)")
        SettingsTextField("Broker URL", mqttBrokerUrl) { url ->
            viewModel.saveMqttSettings(url)
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

        // Info
        Text(
            text = "On-Device LLM: Place a .task model file in the app's files/models/ directory. " +
                "The app will auto-detect and load it on startup.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp)
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun SettingsTextField(
    label: String,
    value: String,
    onSave: (String) -> Unit
) {
    var text by remember(value) { mutableStateOf(value) }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        singleLine = true
    )
    Button(
        onClick = { onSave(text) },
        modifier = Modifier.padding(bottom = 8.dp)
    ) {
        Text("Save")
    }
}

@Composable
private fun SettingsPasswordField(
    label: String,
    value: String,
    onSave: (String) -> Unit
) {
    var text by remember(value) { mutableStateOf(value) }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        singleLine = true,
        visualTransformation = PasswordVisualTransformation()
    )
    Button(
        onClick = { onSave(text) },
        modifier = Modifier.padding(bottom = 8.dp)
    ) {
        Text("Save")
    }
}
