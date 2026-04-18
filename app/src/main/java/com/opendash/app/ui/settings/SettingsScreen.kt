package com.opendash.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.opendash.app.R
import com.opendash.app.ui.settings.locale.LocalePickerRow
import com.opendash.app.ui.settings.news.NewsFeedPickerRow
import com.opendash.app.ui.settings.weather.WeatherLocationPickerRow

@Composable
fun SettingsScreen(
    onBack: (() -> Unit)? = null,
    onOpenSpeakerGroups: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val haBaseUrl by viewModel.haBaseUrl.collectAsState()
    val haToken by viewModel.haToken.collectAsState()
    val openClawUrl by viewModel.openClawUrl.collectAsState()
    val localLlmUrl by viewModel.localLlmUrl.collectAsState()
    val localLlmModel by viewModel.localLlmModel.collectAsState()

    // Settings is shown as an overlay from ModeScaffold AND as a NavHost route
    // from AppNavigation. The NavHost path has no outer inset-consuming parent,
    // so apply `systemBarsPadding()` at the root. When hosted inside
    // ModeScaffold the outer Box already consumed the insets and this becomes
    // a no-op — no double padding.
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.settings_back),
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
            Text(
                stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        Spacer(modifier = Modifier.height(16.dp))

        // === Voice Health (diagnostics) ===
        VoiceHealthSection()
        SettingsDivider()

        // === Voice Interaction ===
        SectionHeader(stringResource(R.string.settings_voice_interaction))
        val continuousMode by viewModel.continuousMode.collectAsState()
        val thinkingSound by viewModel.thinkingSound.collectAsState()
        val bargeInEnabled by viewModel.bargeInEnabled.collectAsState()
        val ttsEnabled by viewModel.ttsEnabled.collectAsState()
        val mediaButtonEnabled by viewModel.mediaButtonEnabled.collectAsState()
        val silenceTimeoutMs by viewModel.silenceTimeoutMs.collectAsState()

        SettingsToggle(stringResource(R.string.settings_read_ai_responses), ttsEnabled) {
            viewModel.saveTtsEnabled(it)
        }
        SettingsToggle(
            stringResource(R.string.settings_continuous_conversation),
            continuousMode
        ) { viewModel.saveContinuousMode(it) }
        SettingsHint(stringResource(R.string.settings_continuous_conversation_hint))
        SettingsToggle(stringResource(R.string.settings_thinking_sound), thinkingSound) {
            viewModel.saveThinkingSound(it)
        }
        SettingsHint(stringResource(R.string.settings_thinking_sound_hint))
        val fillerEnabled by viewModel.fillerPhrasesEnabled.collectAsState()
        SettingsToggle(stringResource(R.string.settings_filler_phrases), fillerEnabled) {
            viewModel.saveFillerPhrasesEnabled(it)
        }
        SettingsHint(stringResource(R.string.settings_filler_phrases_hint))
        val resumeLast by viewModel.resumeLastSession.collectAsState()
        SettingsToggle(stringResource(R.string.settings_resume_last_session), resumeLast) {
            viewModel.saveResumeLastSession(it)
        }
        SettingsHint(stringResource(R.string.settings_resume_last_session_hint))
        SettingsToggle(
            stringResource(R.string.settings_wake_word_interrupts_tts),
            bargeInEnabled
        ) { viewModel.saveBargeInEnabled(it) }
        SettingsToggle(
            stringResource(R.string.settings_media_button_trigger),
            mediaButtonEnabled
        ) { viewModel.saveMediaButtonEnabled(it) }
        SettingsHint(stringResource(R.string.settings_media_button_trigger_hint))

        Text(
            text = stringResource(R.string.settings_silence_timeout, silenceTimeoutMs / 1000.0f),
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

        val minSpeechMs by viewModel.minSpeechMs.collectAsState()
        Text(
            text = stringResource(R.string.settings_min_utterance, minSpeechMs.toInt()),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 8.dp)
        )
        Slider(
            value = minSpeechMs.toFloat(),
            onValueChange = { viewModel.saveMinSpeechMs(it.toLong()) },
            valueRange = 100f..2000f,
            steps = 18,
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
        )
        SettingsHint(stringResource(R.string.settings_min_utterance_hint))

        SettingsDivider()

        // === TTS Provider ===
        SectionHeader(stringResource(R.string.settings_tts_provider))
        val ttsProvider by viewModel.ttsProvider.collectAsState()
        listOf(
            "android" to stringResource(R.string.settings_tts_provider_android),
            "openai" to stringResource(R.string.settings_tts_provider_openai),
            "elevenlabs" to stringResource(R.string.settings_tts_provider_elevenlabs),
            "voicevox" to stringResource(R.string.settings_tts_provider_voicevox),
            "piper" to stringResource(R.string.settings_tts_provider_piper),
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
            SettingsPasswordField(stringResource(R.string.settings_tts_openai_api_key), apiKey) {
                viewModel.saveOpenAiTts(it, voice, model)
            }
            SettingsTextField(stringResource(R.string.settings_tts_openai_voice), voice) {
                viewModel.saveOpenAiTts(apiKey, it, model)
            }
            SettingsTextField(stringResource(R.string.settings_tts_openai_model), model) {
                viewModel.saveOpenAiTts(apiKey, voice, it)
            }
        }

        if (ttsProvider == "elevenlabs") {
            Spacer(modifier = Modifier.height(8.dp))
            val apiKey by viewModel.elevenLabsApiKey.collectAsState()
            val voiceId by viewModel.elevenLabsVoiceId.collectAsState()
            val model by viewModel.elevenLabsModel.collectAsState()
            SettingsPasswordField(stringResource(R.string.settings_tts_elevenlabs_api_key), apiKey) {
                viewModel.saveElevenLabs(it, voiceId, model)
            }
            SettingsTextField(stringResource(R.string.settings_tts_elevenlabs_voice_id), voiceId) {
                viewModel.saveElevenLabs(apiKey, it, model)
            }
            SettingsTextField(stringResource(R.string.settings_tts_elevenlabs_model), model) {
                viewModel.saveElevenLabs(apiKey, voiceId, it)
            }
        }

        if (ttsProvider == "voicevox") {
            Spacer(modifier = Modifier.height(8.dp))
            val baseUrl by viewModel.voicevoxBaseUrl.collectAsState()
            val speakerId by viewModel.voicevoxSpeakerId.collectAsState()
            val termsAccepted by viewModel.voicevoxTermsAccepted.collectAsState()
            SettingsTextField(stringResource(R.string.settings_tts_voicevox_url), baseUrl) {
                viewModel.saveVoiceVox(it, speakerId, termsAccepted)
            }
            SettingsTextField(stringResource(R.string.settings_tts_voicevox_speaker_id), speakerId.toString()) { value ->
                val id = value.toIntOrNull() ?: 3
                viewModel.saveVoiceVox(baseUrl, id, termsAccepted)
            }
            SettingsToggle(
                stringResource(R.string.settings_tts_voicevox_terms),
                termsAccepted
            ) { accepted ->
                viewModel.saveVoiceVox(baseUrl, speakerId, accepted)
            }
            SettingsHint(stringResource(R.string.settings_tts_voicevox_hint))
        }

        SettingsDivider()

        // === Text-to-Speech (Android system settings) ===
        val ttsSpeechRate by viewModel.ttsSpeechRate.collectAsState()
        val ttsPitch by viewModel.ttsPitch.collectAsState()
        val ttsEngine by viewModel.ttsEngine.collectAsState()
        val availableEngines by viewModel.availableEngines.collectAsState()

        SectionHeader(stringResource(R.string.settings_android_tts_section))

        Text(
            text = stringResource(R.string.settings_speech_rate, ttsSpeechRate),
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
            text = stringResource(R.string.settings_pitch, ttsPitch),
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
            val systemDefaultLabel = stringResource(R.string.settings_tts_engine_default)
            val engineName = availableEngines.find { it.packageName == ttsEngine }?.label
                ?: ttsEngine.ifEmpty { systemDefaultLabel }
            Text(
                text = stringResource(R.string.settings_tts_engine, engineName),
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

        // === Weather ===
        // Searchable city picker (Phase B of docs/roadmap.md UX polish).
        // Keep `viewModel.saveDefaultLocation` around on the SettingsViewModel
        // for compatibility with any other callers; the picker row bypasses
        // it and writes via its own dedicated ViewModel instead.
        SectionHeader(stringResource(R.string.settings_weather_section))
        WeatherLocationPickerRow()
        SettingsHint(stringResource(R.string.settings_weather_hint))

        SettingsDivider()

        // === News ===
        SectionHeader(stringResource(R.string.news_feed_section_header))
        NewsFeedPickerRow()
        SettingsHint(stringResource(R.string.settings_news_hint))

        SettingsDivider()

        // === App Language ===
        SectionHeader(stringResource(R.string.settings_app_language_section))
        LocalePickerRow()
        SettingsHint(stringResource(R.string.settings_app_language_hint))

        SettingsDivider()

        // === Speech Recognition ===
        SectionHeader(stringResource(R.string.settings_speech_recognition_section))
        val sttProvider by viewModel.sttProviderType.collectAsState()
        listOf(
            "android" to stringResource(R.string.settings_stt_provider_android),
            "vosk" to stringResource(R.string.settings_stt_provider_vosk),
            "whisper" to stringResource(R.string.settings_stt_provider_whisper),
        ).forEach { (id, label) ->
            val isSelected = sttProvider == id
            OutlinedButton(
                onClick = { viewModel.saveSttProviderType(id) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                colors = if (isSelected) ButtonDefaults.outlinedButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ) else ButtonDefaults.outlinedButtonColors()
            ) {
                Text(label, color = MaterialTheme.colorScheme.onSurface)
            }
        }
        SettingsHint(stringResource(R.string.settings_stt_provider_hint))

        val sttLanguage by viewModel.sttLanguage.collectAsState()
        SettingsTextField(stringResource(R.string.settings_stt_language), sttLanguage) { lang ->
            viewModel.saveSttLanguage(lang)
        }
        SettingsHint(stringResource(R.string.settings_language_default_hint))

        val ttsLanguageVal by viewModel.ttsLanguage.collectAsState()
        SettingsTextField(stringResource(R.string.settings_tts_language), ttsLanguageVal) { lang ->
            viewModel.saveTtsLanguage(lang)
        }
        SettingsHint(stringResource(R.string.settings_language_default_hint))

        SettingsDivider()

        // === Wake Word ===
        SectionHeader(stringResource(R.string.settings_wake_word_section))
        val hotwordEnabled by viewModel.hotwordEnabled.collectAsState()
        SettingsToggle(stringResource(R.string.settings_enable_wake_word), hotwordEnabled) { viewModel.saveHotwordEnabled(it) }
        SettingsHint(stringResource(R.string.settings_enable_wake_word_hint))

        val wakeWord by viewModel.wakeWord.collectAsState()
        SettingsTextField(stringResource(R.string.settings_wake_word_phrase), wakeWord) { word ->
            viewModel.saveWakeWord(word)
        }
        SettingsHint(stringResource(R.string.settings_wake_word_phrase_hint))

        val wakeWordSensitivity by viewModel.wakeWordSensitivity.collectAsState()
        Text(
            text = stringResource(R.string.settings_sensitivity, wakeWordSensitivity),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 4.dp)
        )
        Slider(
            value = wakeWordSensitivity,
            onValueChange = { viewModel.saveWakeWordSensitivity(it) },
            valueRange = 0f..1f,
            steps = 9,
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
        )
        SettingsHint(stringResource(R.string.settings_sensitivity_hint))

        val batterySaver by viewModel.batterySaverEnabled.collectAsState()
        SettingsToggle(stringResource(R.string.settings_battery_saver), batterySaver) { viewModel.saveBatterySaverEnabled(it) }
        SettingsHint(stringResource(R.string.settings_battery_saver_hint))

        val multiroomOn by viewModel.multiroomBroadcastEnabled.collectAsState()
        SettingsToggle(stringResource(R.string.settings_multiroom_broadcast), multiroomOn) { viewModel.saveMultiroomBroadcastEnabled(it) }
        SettingsHint(stringResource(R.string.settings_multiroom_broadcast_hint))

        val multiroomSecret by viewModel.multiroomSecret.collectAsState()
        SettingsPasswordField(stringResource(R.string.settings_multiroom_secret), multiroomSecret) { value ->
            viewModel.saveMultiroomSecret(value)
        }
        SettingsHint(stringResource(R.string.settings_multiroom_secret_hint))

        SettingsMultiroomPairingCard(secret = multiroomSecret)

        if (onOpenSpeakerGroups != null) {
            OutlinedButton(
                onClick = onOpenSpeakerGroups,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Text(stringResource(R.string.settings_speaker_groups_button), color = MaterialTheme.colorScheme.onSurface)
            }
            SettingsHint(stringResource(R.string.settings_speaker_groups_button_hint))
        }

        SettingsDivider()

        // === Connections ===
        SectionHeader(stringResource(R.string.settings_home_assistant_section))
        SettingsTextField(stringResource(R.string.settings_ha_base_url), haBaseUrl) { url ->
            viewModel.saveHaSettings(url, haToken)
        }
        SettingsPasswordField(stringResource(R.string.settings_ha_token), haToken) { token ->
            viewModel.saveHaSettings(haBaseUrl, token)
        }

        SettingsDivider()

        SectionHeader(stringResource(R.string.settings_openclaw_section))
        SettingsTextField(stringResource(R.string.settings_openclaw_url), openClawUrl) { url ->
            viewModel.saveOpenClawSettings(url)
        }

        SettingsDivider()

        SectionHeader(stringResource(R.string.settings_local_llm_section))
        SettingsTextField(stringResource(R.string.settings_local_llm_url), localLlmUrl) { url ->
            viewModel.saveLocalLlmSettings(url, localLlmModel)
        }
        SettingsTextField(stringResource(R.string.settings_local_llm_model), localLlmModel) { model ->
            viewModel.saveLocalLlmSettings(localLlmUrl, model)
        }

        SettingsDivider()

        val switchBotToken by viewModel.switchBotToken.collectAsState()
        val switchBotSecret by viewModel.switchBotSecret.collectAsState()
        SectionHeader(stringResource(R.string.settings_switchbot_section))
        SettingsTextField(stringResource(R.string.settings_switchbot_token), switchBotToken) { token ->
            viewModel.saveSwitchBotSettings(token, switchBotSecret)
        }
        SettingsPasswordField(stringResource(R.string.settings_switchbot_secret), switchBotSecret) { secret ->
            viewModel.saveSwitchBotSettings(switchBotToken, secret)
        }

        SettingsDivider()

        val mqttBrokerUrl by viewModel.mqttBrokerUrl.collectAsState()
        SectionHeader(stringResource(R.string.settings_mqtt_section))
        SettingsTextField(stringResource(R.string.settings_mqtt_broker_url), mqttBrokerUrl) { url ->
            viewModel.saveMqttSettings(url)
        }

        SettingsDivider()

        Text(
            text = stringResource(R.string.settings_about_litert),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        Text(
            text = stringResource(R.string.settings_about_default_assistant),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 4.dp)
        )

        SettingsDivider()

        AboutSection()

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun AboutSection() {
    val context = androidx.compose.ui.platform.LocalContext.current
    SectionHeader(stringResource(R.string.settings_about_section))
    Text(
        text = stringResource(R.string.settings_about_tagline),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface
    )
    Spacer(Modifier.height(8.dp))
    OutlinedButton(
        onClick = {
            val intent = android.content.Intent(
                android.content.Intent.ACTION_VIEW,
                android.net.Uri.parse(
                    "https://github.com/yuga-hashimoto/open-dash"
                )
            ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(intent) }
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(stringResource(R.string.settings_about_github))
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
        Text(stringResource(R.string.common_save))
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
        Text(stringResource(R.string.common_save))
    }
}
