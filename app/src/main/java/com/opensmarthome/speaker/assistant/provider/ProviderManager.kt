package com.opensmarthome.speaker.assistant.provider

import android.content.Context
import com.opensmarthome.speaker.assistant.provider.embedded.EmbeddedLlmConfig
import com.opensmarthome.speaker.assistant.provider.embedded.EmbeddedLlmProvider
import com.opensmarthome.speaker.assistant.provider.embedded.HardwareProfile
import com.opensmarthome.speaker.assistant.provider.embedded.ModelManager
import com.opensmarthome.speaker.assistant.skills.SkillRegistry
import com.opensmarthome.speaker.device.DeviceManager
import com.opensmarthome.speaker.assistant.provider.openai.OpenAiCompatibleConfig
import com.opensmarthome.speaker.assistant.provider.openai.OpenAiCompatibleProvider
import com.opensmarthome.speaker.assistant.provider.openclaw.OpenClawConfig
import com.opensmarthome.speaker.assistant.provider.openclaw.OpenClawProvider
import com.opensmarthome.speaker.assistant.router.ConversationRouter
import com.opensmarthome.speaker.data.preferences.AppPreferences
import com.opensmarthome.speaker.data.preferences.PreferenceKeys
import com.opensmarthome.speaker.data.preferences.SecurePreferences
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProviderManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val router: ConversationRouter,
    private val preferences: AppPreferences,
    private val securePreferences: SecurePreferences,
    private val client: OkHttpClient,
    private val moshi: Moshi,
    private val skillRegistry: SkillRegistry,
    private val deviceManager: DeviceManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val modelManager = ModelManager(context)

    fun initialize() {
        scope.launch {
            registerEmbeddedLlm()
            registerOpenClawIfConfigured()
            registerOpenAiCompatibleIfConfigured()
        }
    }

    private suspend fun registerEmbeddedLlm() {
        try {
            val models = modelManager.listAvailableModels()
            if (models.isNotEmpty()) {
                val modelPath = models.first().path
                // User-customized system prompt overrides the default when set
                val customPrompt = preferences.observe(PreferenceKeys.CUSTOM_SYSTEM_PROMPT).first()
                val systemPrompt = customPrompt?.takeIf { it.isNotBlank() }
                    ?: EmbeddedLlmConfig.DEFAULT_SYSTEM_PROMPT
                // Tune context size / thread count / GPU layers to device hardware
                val profile = HardwareProfile.fromContext(context)
                val config = EmbeddedLlmConfig.forHardware(
                    modelPath = modelPath,
                    profile = profile,
                    systemPrompt = systemPrompt
                )
                Timber.d("EmbeddedLlm tuned for ${profile.tier}: ctx=${config.contextSize} threads=${config.threads}")
                val provider = EmbeddedLlmProvider(
                    context = context,
                    config = config,
                    skillRegistry = skillRegistry,
                    deviceManager = deviceManager
                )
                router.registerProvider(provider)
                Timber.d("Registered EmbeddedLlmProvider with model: ${models.first().name} (custom prompt: ${!customPrompt.isNullOrBlank()})")
                // Pre-warm the engine in the background so the first user
                // request doesn't pay the GPU/CPU init cost.
                scope.launch { provider.warmUp() }
            } else {
                Timber.d("No models found, EmbeddedLlmProvider not registered")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to register EmbeddedLlmProvider")
        }
    }

    private suspend fun registerOpenClawIfConfigured() {
        try {
            val url = preferences.observe(PreferenceKeys.OPENCLAW_GATEWAY_URL).first()
            if (!url.isNullOrBlank()) {
                val provider = OpenClawProvider(
                    client = client,
                    moshi = moshi,
                    config = OpenClawConfig(gatewayUrl = url)
                )
                router.registerProvider(provider)
                Timber.d("Registered OpenClawProvider: $url")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to register OpenClawProvider")
        }
    }

    private suspend fun registerOpenAiCompatibleIfConfigured() {
        try {
            val url = preferences.observe(PreferenceKeys.LOCAL_LLM_BASE_URL).first()
            val model = preferences.observe(PreferenceKeys.LOCAL_LLM_MODEL).first()
            if (!url.isNullOrBlank()) {
                val provider = OpenAiCompatibleProvider(
                    client = client,
                    moshi = moshi,
                    config = OpenAiCompatibleConfig(
                        baseUrl = url,
                        model = model ?: "gemma-4-e2b"
                    )
                )
                router.registerProvider(provider)
                Timber.d("Registered OpenAiCompatibleProvider: $url")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to register OpenAiCompatibleProvider")
        }
    }

    fun getModelManager(): ModelManager = modelManager
}
