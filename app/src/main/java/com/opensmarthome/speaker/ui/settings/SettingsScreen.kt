package com.opensmarthome.speaker.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
    onBack: (() -> Unit)? = null,
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
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
            Text(
                "Settings",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
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

        // Wake Word Section
        val wakeWord by viewModel.wakeWord.collectAsState()
        SectionHeader("Wake Word")
        SettingsTextField("Wake Word", wakeWord) { word ->
            viewModel.saveWakeWord(word)
        }
        Text(
            text = "The phrase the app listens for to start a conversation. Restart the app after changing.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

        // TTS Section
        val ttsSpeechRate by viewModel.ttsSpeechRate.collectAsState()
        val ttsPitch by viewModel.ttsPitch.collectAsState()
        val ttsEngine by viewModel.ttsEngine.collectAsState()
        val availableEngines by viewModel.availableEngines.collectAsState()

        SectionHeader("Text-to-Speech")

        Text(
            text = "Speech Rate: ${"%.1f".format(ttsSpeechRate)}x",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 4.dp)
        )
        androidx.compose.material3.Slider(
            value = ttsSpeechRate,
            onValueChange = { viewModel.saveTtsSpeechRate(it) },
            valueRange = 0.5f..2.0f,
            steps = 5,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
        )

        Text(
            text = "Pitch: ${"%.1f".format(ttsPitch)}x",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 4.dp)
        )
        androidx.compose.material3.Slider(
            value = ttsPitch,
            onValueChange = { viewModel.saveTtsPitch(it) },
            valueRange = 0.5f..2.0f,
            steps = 5,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
        )

        if (availableEngines.isNotEmpty()) {
            Text(
                text = "TTS Engine: ${
                    availableEngines.find { it.packageName == ttsEngine }?.label
                        ?: ttsEngine.ifEmpty { "System Default" }
                }",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
            )
            availableEngines.forEach { engine ->
                val isSelected = engine.packageName == ttsEngine
                androidx.compose.material3.OutlinedButton(
                    onClick = { viewModel.saveTtsEngine(engine.packageName) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    colors = if (isSelected) {
                        androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    } else {
                        androidx.compose.material3.ButtonDefaults.outlinedButtonColors()
                    }
                ) {
                    Text(
                        engine.label,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

        // Info
        Text(
            text = "On-Device LLM: The app automatically downloads and loads a Gemma model on first launch.",
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
        label = { Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface)
    )
    Button(
        onClick = { onSave(text) },
        modifier = Modifier.padding(bottom = 8.dp),
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
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
        label = { Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface)
    )
    Button(
        onClick = { onSave(text) },
        modifier = Modifier.padding(bottom = 8.dp),
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    ) {
        Text("Save")
    }
}
