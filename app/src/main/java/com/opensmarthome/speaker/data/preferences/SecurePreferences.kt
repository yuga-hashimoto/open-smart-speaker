package com.opensmarthome.speaker.data.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keystore-backed encrypted preferences for credentials.
 *
 * Secrets NEVER fall back to plaintext SharedPreferences — if the keystore
 * is unavailable, reads return empty and writes no-op, forcing the user to
 * re-enter credentials rather than silently leaking them to disk.
 */
@Singleton
class SecurePreferences @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences? = try {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            "secure_prefs",
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        // Refuse to fall back to plaintext — credentials are lost but never
        // written unencrypted to disk.
        Timber.e(e, "EncryptedSharedPreferences unavailable; credentials won't be persisted")
        null
    }

    val isAvailable: Boolean get() = prefs != null

    fun getString(key: String, default: String = ""): String =
        prefs?.getString(key, default) ?: default

    fun putString(key: String, value: String) {
        prefs?.edit()?.putString(key, value)?.apply()
            ?: Timber.w("Refusing to persist secret for key=$key: encrypted prefs unavailable")
    }

    fun remove(key: String) {
        prefs?.edit()?.remove(key)?.apply()
    }

    fun contains(key: String): Boolean = prefs?.contains(key) == true

    companion object {
        const val KEY_HA_TOKEN = "ha_token"
        const val KEY_OPENCLAW_API_KEY = "openclaw_api_key"
        const val KEY_LOCAL_LLM_API_KEY = "local_llm_api_key"
        const val KEY_OPENAI_TTS_API_KEY = "openai_tts_api_key"
        const val KEY_ELEVENLABS_API_KEY = "elevenlabs_api_key"
        const val KEY_SWITCHBOT_TOKEN = "switchbot_token"
        const val KEY_SWITCHBOT_SECRET = "switchbot_secret"
        const val KEY_MQTT_PASSWORD = "mqtt_password"
        /** Multi-room shared secret (HMAC-SHA256). Set manually until QR-pair lands (P17.6). */
        const val KEY_MULTIROOM_SECRET = "multiroom_secret"
    }
}
