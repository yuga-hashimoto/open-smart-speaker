package com.opensmarthome.speaker.assistant.provider

import android.content.Context
import com.opensmarthome.speaker.assistant.provider.embedded.EmbeddedLlmConfig
import com.opensmarthome.speaker.assistant.provider.embedded.EmbeddedLlmProvider
import com.opensmarthome.speaker.assistant.provider.embedded.LlamaCppBridge
import com.opensmarthome.speaker.assistant.provider.embedded.ModelManager
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
    private val moshi: Moshi
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val llamaBridge = LlamaCppBridge()
    private val modelManager = ModelManager(context)

    fun initialize() {
        scope.launch {
            registerEmbeddedLlm()
            registerOpenClawIfConfigured()
            registerOpenAiCompatibleIfConfigured()
        }
    }

    private suspend fun registerEmbeddedLlm() {
        val models = modelManager.listAvailableModels()
        if (models.isNotEmpty()) {
            val modelPath = models.first().path
            val provider = EmbeddedLlmProvider(
                bridge = llamaBridge,
                config = EmbeddedLlmConfig(modelPath = modelPath)
            )
            router.registerProvider(provider)
            Timber.d("Registered EmbeddedLlmProvider with model: ${models.first().name}")
        } else {
            Timber.d("No GGUF models found, EmbeddedLlmProvider not registered")
        }
    }

    private suspend fun registerOpenClawIfConfigured() {
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
    }

    private suspend fun registerOpenAiCompatibleIfConfigured() {
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
    }

    fun getModelManager(): ModelManager = modelManager
}
