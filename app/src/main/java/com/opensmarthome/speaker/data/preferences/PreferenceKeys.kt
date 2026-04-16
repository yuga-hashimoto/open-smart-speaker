package com.opensmarthome.speaker.data.preferences

import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

object PreferenceKeys {
    // TTS
    val TTS_SPEECH_RATE = floatPreferencesKey("tts_speech_rate")
    val TTS_PITCH = floatPreferencesKey("tts_pitch")
    val TTS_ENGINE = stringPreferencesKey("tts_engine")
    val TTS_LANGUAGE = stringPreferencesKey("tts_language")
    val HA_BASE_URL = stringPreferencesKey("ha_base_url")
    val HA_TOKEN = stringPreferencesKey("ha_token")
    val OPENCLAW_GATEWAY_URL = stringPreferencesKey("openclaw_gateway_url")
    val OPENCLAW_API_KEY = stringPreferencesKey("openclaw_api_key")
    val LOCAL_LLM_BASE_URL = stringPreferencesKey("local_llm_base_url")
    val LOCAL_LLM_MODEL = stringPreferencesKey("local_llm_model")
    val ROUTING_POLICY = stringPreferencesKey("routing_policy")
    val ACTIVE_PROVIDER_ID = stringPreferencesKey("active_provider_id")
    val SWITCHBOT_TOKEN = stringPreferencesKey("switchbot_token")
    val SWITCHBOT_SECRET = stringPreferencesKey("switchbot_secret")
    val MQTT_BROKER_URL = stringPreferencesKey("mqtt_broker_url")
    val MQTT_USERNAME = stringPreferencesKey("mqtt_username")
    val MQTT_PASSWORD = stringPreferencesKey("mqtt_password")
    val WAKE_WORD = stringPreferencesKey("wake_word")
}
