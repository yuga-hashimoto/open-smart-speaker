package com.opensmarthome.speaker.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
        Row(verticalAlignment = Alignment.CenterVertically) {
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

        // === Voice Interaction ===
        SectionHeader("Voice Interaction")
        val continuousMode by viewModel.continuousMode.collectAsState()
        val thinkingSound by viewModel.thinkingSound.collectAsState()
        val bargeInEnabled by viewModel.bargeInEnabled.collectAsState()
        val ttsEnabled by viewModel.ttsEnabled.collectAsState()
        val mediaButtonEnabled by viewModel.mediaButtonEnabled.collectAsState()
        val silenceTimeoutMs by viewModel.silenceTimeoutMs.collectAsState()

        SettingsToggle("Read AI Responses Aloud", ttsEnabled) { viewModel.saveTtsEnabled(it) }
        SettingsToggle("Continuous Conversation", continuousMode) { viewModel.saveContinuousMode(it) }
        SettingsHint("Auto-restart listening after response finishes")
        SettingsToggle("Thinking Sound", thinkingSound) { viewModel.saveThinkingSound(it) }
        SettingsHint("Play a beep when processing starts")
        SettingsToggle("Wake Word Interrupts TTS", bargeInEnabled) { viewModel.saveBargeInEnabled(it) }
        SettingsToggle("Media Button Trigger", mediaButtonEnabled) { viewModel.saveMediaButtonEnabled(it) }
        SettingsHint("Use Bluetooth headset button to start voice input")

        Text(
            text = "Silence Timeout: ${silenceTimeoutMs / 1000.0}s",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 8.dp)
        )
        Slider(
            value = silenceTimeoutMs.toFloat(),
            onValueChange = { viewModel.saveSilenceTimeout(it.toLong()) },
            valueRange = 1000f..10000f,
            steps = 8,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
        )

        SettingsDivider()

        // === TTS Provider ===
        SectionHeader("TTS Provider")
        val ttsProvider by viewModel.ttsProvider.collectAsState()
        listOf(
            "android" to "Android System (on-device, free)",
            "openai" to "OpenAI TTS (cloud, natural)",
            "elevenlabs" to "ElevenLabs (cloud, high quality)",
            "voicevox" to "VOICEVOX (self-hosted, Japanese)"
        ).forEach { (id, label) ->
            val isSelected = ttsProvider == id
            OutlinedButton(
                onClick = { viewModel.saveTtsProvider(id) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                colors = if (isSelected) ButtonDefaults.outlinedButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ) else ButtonDefaults.outlinedButtonColors()
            ) {
                Text(label, color = MaterialTheme.colorScheme.onSurface)
            }
        }

        if (ttsProvider == "openai") {
            Spacer(modifier = Modifier.height(8.dp))
            val apiKey by viewModel.openAiTtsApiKey.collectAsState()
            val voice by viewModel.openAiTtsVoice.collectAsState()
            val model by viewModel.openAiTtsModel.collectAsState()
            SettingsPasswordField("OpenAI API Key", apiKey) {
                viewModel.saveOpenAiTts(it, voice, model)
            }
            SettingsTextField("Voice (alloy/echo/fable/onyx/nova/shimmer/coral)", voice) {
                viewModel.saveOpenAiTts(apiKey, it, model)
            }
            SettingsTextField("Model (tts-1 / tts-1-hd / gpt-4o-mini-tts)", model) {
                viewModel.saveOpenAiTts(apiKey, voice, it)
            }
        }

        if (ttsProvider == "elevenlabs") {
            Spacer(modifier = Modifier.height(8.dp))
            val apiKey by viewModel.elevenLabsApiKey.collectAsState()
            val voiceId by viewModel.elevenLabsVoiceId.collectAsState()
            val model by viewModel.elevenLabsModel.collectAsState()
            SettingsPasswordField("ElevenLabs API Key", apiKey) {
                viewModel.saveElevenLabs(it, voiceId, model)
            }
            SettingsTextField("Voice ID", voiceId) {
                viewModel.saveElevenLabs(apiKey, it, model)
            }
            SettingsTextField("Model (eleven_multilingual_v2)", model) {
                viewModel.saveElevenLabs(apiKey, voiceId, it)
            }
        }

        if (ttsProvider == "voicevox") {
            Spacer(modifier = Modifier.height(8.dp))
            val baseUrl by viewModel.voicevoxBaseUrl.collectAsState()
            val speakerId by viewModel.voicevoxSpeakerId.collectAsState()
            val termsAccepted by viewModel.voicevoxTermsAccepted.collectAsState()
            SettingsTextField("VOICEVOX Engine URL (e.g. http://192.168.1.10:50021)", baseUrl) {
                viewModel.saveVoiceVox(it, speakerId, termsAccepted)
            }
            SettingsTextField("Speaker/Style ID (3 = ずんだもん)", speakerId.toString()) { value ->
                val id = value.toIntOrNull() ?: 3
                viewModel.saveVoiceVox(baseUrl, id, termsAccepted)
            }
            SettingsToggle(
                "I agree to VOICEVOX terms of use (credit the speaker per their license)",
                termsAccepted
            ) { accepted ->
                viewModel.saveVoiceVox(baseUrl, speakerId, accepted)
            }
            SettingsHint("Run the VOICEVOX ENGINE (Docker/PC) on your LAN. Speech won't work until terms are accepted.")
        }

        SettingsDivider()

        // === Text-to-Speech (Android system settings) ===
        val ttsSpeechRate by viewModel.ttsSpeechRate.collectAsState()
        val ttsPitch by viewModel.ttsPitch.collectAsState()
        val ttsEngine by viewModel.ttsEngine.collectAsState()
        val availableEngines by viewModel.availableEngines.collectAsState()

        SectionHeader("Android System TTS")

        Text(
            text = "Speech Rate: ${"%.1f".format(ttsSpeechRate)}x",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 4.dp)
        )
        Slider(
            value = ttsSpeechRate,
            onValueChange = { viewModel.saveTtsSpeechRate(it) },
            valueRange = 0.5f..3.0f,
            steps = 9,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
        )

        Text(
            text = "Pitch: ${"%.1f".format(ttsPitch)}x",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 4.dp)
        )
        Slider(
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
                OutlinedButton(
                    onClick = { viewModel.saveTtsEngine(engine.packageName) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    colors = if (isSelected) {
                        ButtonDefaults.outlinedButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    } else {
                        ButtonDefaults.outlinedButtonColors()
                    }
                ) {
                    Text(engine.label, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }

        SettingsDivider()

        // === Speech Recognition ===
        SectionHeader("Speech Recognition")
        val sttLanguage by viewModel.sttLanguage.collectAsState()
        SettingsTextField("STT Language (e.g. ja-JP, en-US)", sttLanguage) { lang ->
            viewModel.saveSttLanguage(lang)
        }
        SettingsHint("Leave empty to use device default language")

        val ttsLanguageVal by viewModel.ttsLanguage.collectAsState()
        SettingsTextField("TTS Language (e.g. ja-JP, en-US)", ttsLanguageVal) { lang ->
            viewModel.saveTtsLanguage(lang)
        }
        SettingsHint("Leave empty to use device default language")

        SettingsDivider()

        // === Wake Word ===
        SectionHeader("Wake Word")
        val hotwordEnabled by viewModel.hotwordEnabled.collectAsState()
        SettingsToggle("Enable Wake Word", hotwordEnabled) { viewModel.saveHotwordEnabled(it) }
        SettingsHint("Off = wake word detection disabled, mic only activates from mic button")

        val wakeWord by viewModel.wakeWord.collectAsState()
        SettingsTextField("Wake Word Phrase", wakeWord) { word ->
            viewModel.saveWakeWord(word)
        }
        SettingsHint("The phrase the app listens for. Restart the app after changing.")

        SettingsDivider()

        // === Connections ===
        SectionHeader("Home Assistant")
        SettingsTextField("Base URL", haBaseUrl) { url ->
            viewModel.saveHaSettings(url, haToken)
        }
        SettingsPasswordField("Long-Lived Access Token", haToken) { token ->
            viewModel.saveHaSettings(haBaseUrl, token)
        }

        SettingsDivider()

        SectionHeader("OpenClaw")
        SettingsTextField("Gateway URL", openClawUrl) { url ->
            viewModel.saveOpenClawSettings(url)
        }

        SettingsDivider()

        SectionHeader("Local LLM (OpenAI Compatible)")
        SettingsTextField("Endpoint URL", localLlmUrl) { url ->
            viewModel.saveLocalLlmSettings(url, localLlmModel)
        }
        SettingsTextField("Model Name", localLlmModel) { model ->
            viewModel.saveLocalLlmSettings(localLlmUrl, model)
        }

        SettingsDivider()

        val switchBotToken by viewModel.switchBotToken.collectAsState()
        val switchBotSecret by viewModel.switchBotSecret.collectAsState()
        SectionHeader("SwitchBot")
        SettingsTextField("Token", switchBotToken) { token ->
            viewModel.saveSwitchBotSettings(token, switchBotSecret)
        }
        SettingsPasswordField("Secret Key", switchBotSecret) { secret ->
            viewModel.saveSwitchBotSettings(switchBotToken, secret)
        }

        SettingsDivider()

        val mqttBrokerUrl by viewModel.mqttBrokerUrl.collectAsState()
        SectionHeader("MQTT (Shelly / Tasmota)")
        SettingsTextField("Broker URL", mqttBrokerUrl) { url ->
            viewModel.saveMqttSettings(url)
        }

        SettingsDivider()

        Text(
            text = "On-Device LLM: The app uses LiteRT-LM for GPU-accelerated inference with Gemma 4 E2B.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        Text(
            text = "To set as default assistant: Settings > Apps > Default Apps > Digital Assistant > OpenSmartSpeaker",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 4.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))
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
private fun SettingsToggle(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
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
        colors = ButtonDefaults.buttonColors(
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
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    ) {
        Text("Save")
    }
}
