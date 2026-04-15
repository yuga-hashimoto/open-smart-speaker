package com.opensmarthome.speaker.data.preferences

import androidx.datastore.preferences.core.stringPreferencesKey

object PreferenceKeys {
    val HA_BASE_URL = stringPreferencesKey("ha_base_url")
    val HA_TOKEN = stringPreferencesKey("ha_token")
    val OPENCLAW_GATEWAY_URL = stringPreferencesKey("openclaw_gateway_url")
    val OPENCLAW_API_KEY = stringPreferencesKey("openclaw_api_key")
    val LOCAL_LLM_BASE_URL = stringPreferencesKey("local_llm_base_url")
    val LOCAL_LLM_MODEL = stringPreferencesKey("local_llm_model")
    val ROUTING_POLICY = stringPreferencesKey("routing_policy")
    val ACTIVE_PROVIDER_ID = stringPreferencesKey("active_provider_id")
}
