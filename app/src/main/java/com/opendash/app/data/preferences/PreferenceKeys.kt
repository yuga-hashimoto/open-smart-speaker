package com.opendash.app.data.preferences

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

object PreferenceKeys {
    // TTS
    val TTS_SPEECH_RATE = floatPreferencesKey("tts_speech_rate")
    val TTS_PITCH = floatPreferencesKey("tts_pitch")
    val TTS_ENGINE = stringPreferencesKey("tts_engine")
    val TTS_LANGUAGE = stringPreferencesKey("tts_language")
    val TTS_ENABLED = booleanPreferencesKey("tts_enabled")

    // TTS Provider ("android" | "openai" | "elevenlabs" | "voicevox")
    val TTS_PROVIDER = stringPreferencesKey("tts_provider")
    val OPENAI_TTS_VOICE = stringPreferencesKey("openai_tts_voice")   // alloy/echo/fable/onyx/nova/shimmer/coral
    val OPENAI_TTS_MODEL = stringPreferencesKey("openai_tts_model")   // tts-1 / tts-1-hd / gpt-4o-mini-tts
    val ELEVENLABS_VOICE_ID = stringPreferencesKey("elevenlabs_voice_id")
    val ELEVENLABS_MODEL = stringPreferencesKey("elevenlabs_model")    // eleven_multilingual_v2
    val ELEVENLABS_SPEED = floatPreferencesKey("elevenlabs_speed")     // 0.7 - 1.2
    val VOICEVOX_SPEAKER_ID = intPreferencesKey("voicevox_speaker_id")
    val VOICEVOX_STYLE_ID = intPreferencesKey("voicevox_style_id")
    val VOICEVOX_TERMS_ACCEPTED = booleanPreferencesKey("voicevox_terms_accepted")
    val VOICEVOX_BASE_URL = stringPreferencesKey("voicevox_base_url") // self-hosted HTTP engine URL

    // Voice interaction
    val CONTINUOUS_MODE = booleanPreferencesKey("continuous_mode")
    val THINKING_SOUND = booleanPreferencesKey("thinking_sound")
    val BARGE_IN_ENABLED = booleanPreferencesKey("barge_in_enabled")
    val SILENCE_TIMEOUT_MS = longPreferencesKey("silence_timeout_ms")
    /**
     * Minimum detected speech length, in ms, before we accept the utterance as
     * real input. Android's built-in `EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS`
     * reads this today; the forthcoming offline VAD backend will also honour
     * it to filter brief noises (chair squeaks, lip smacks).
     */
    val MIN_SPEECH_MS = longPreferencesKey("min_speech_ms")
    val MEDIA_BUTTON_ENABLED = booleanPreferencesKey("media_button_enabled")

    // STT
    val STT_LANGUAGE = stringPreferencesKey("stt_language")
    /** Which STT backend to use: "android" (default) | "vosk" | "whisper". */
    val STT_PROVIDER_TYPE = stringPreferencesKey("stt_provider_type")

    // Home Assistant (secrets live in SecurePreferences; only URL is plaintext)
    val HA_BASE_URL = stringPreferencesKey("ha_base_url")

    // OpenClaw (API key lives in SecurePreferences; only URL is plaintext)
    val OPENCLAW_GATEWAY_URL = stringPreferencesKey("openclaw_gateway_url")

    // Local LLM
    val LOCAL_LLM_BASE_URL = stringPreferencesKey("local_llm_base_url")
    val LOCAL_LLM_MODEL = stringPreferencesKey("local_llm_model")

    // Routing
    val ROUTING_POLICY = stringPreferencesKey("routing_policy")
    val ACTIVE_PROVIDER_ID = stringPreferencesKey("active_provider_id")

    // SwitchBot (token + secret live in SecurePreferences — both are credentials)

    // MQTT (password lives in SecurePreferences; URL + username are plaintext)
    val MQTT_BROKER_URL = stringPreferencesKey("mqtt_broker_url")
    val MQTT_USERNAME = stringPreferencesKey("mqtt_username")

    // Wake word
    val WAKE_WORD = stringPreferencesKey("wake_word")
    val HOTWORD_ENABLED = booleanPreferencesKey("hotword_enabled")
    val WAKE_WORD_SENSITIVITY = floatPreferencesKey("wake_word_sensitivity")
    /** When true, wake-word detection pauses while battery is low and unplugged. */
    val BATTERY_SAVER_ENABLED = booleanPreferencesKey("battery_saver_enabled")

    /** When true, advertise this device on the LAN via mDNS so peers can discover it. */
    val MULTIROOM_BROADCAST_ENABLED = booleanPreferencesKey("multiroom_broadcast_enabled")

    /**
     * User-chosen default location for `get_weather` / `get_forecast` when
     * the utterance does not explicitly name a place (e.g. "what's the
     * weather?"). Empty / unset means the tool's provider falls back to its
     * own built-in default (currently "Tokyo", preserved for backward
     * compatibility with pre-setting installs).
     *
     * IMPORTANT: this must be a simple city name (e.g. `"Munakata"`),
     * NOT a human-facing `"Munakata, Fukuoka, Japan"` label. The
     * Open-Meteo geocoding endpoint's `name=` parameter only matches a
     * single locality — passing a comma-separated label returns zero
     * results. The Settings picker stores the full label in
     * [DEFAULT_LOCATION_DISPLAY_LABEL] for UI rendering only.
     */
    val DEFAULT_LOCATION = stringPreferencesKey("default_location")

    /**
     * Human-facing label (`"Munakata, Fukuoka, Japan"`) for the Weather
     * location picker row in Settings. Rendered verbatim; never sent to
     * the geocoding API — see [DEFAULT_LOCATION] for the API-facing value.
     * Empty / unset means the row falls back to rendering the raw
     * [DEFAULT_LOCATION] value (or the bundled "Tokyo (built-in default)"
     * string for first-run / cleared installs).
     */
    val DEFAULT_LOCATION_DISPLAY_LABEL = stringPreferencesKey("default_location_display_label")

    // Setup
    val SETUP_COMPLETED = booleanPreferencesKey("setup_completed")

    // Filler phrases (keep user engaged during LLM processing)
    val FILLER_PHRASES_ENABLED = booleanPreferencesKey("filler_phrases_enabled")

    // Resume last session (restore conversation history from Room on launch)
    val RESUME_LAST_SESSION = booleanPreferencesKey("resume_last_session")

    // Agent customization
    val CUSTOM_SYSTEM_PROMPT = stringPreferencesKey("custom_system_prompt")

    /**
     * User-selected UI locale as a BCP-47 language tag ("", "ja", "es",
     * "fr", "de", "zh-CN", etc.). Blank / unset means "follow the system
     * locale" — mirrors openclaw-assistant's `appLanguage` semantics so
     * the empty string is always a valid "no override" value.
     *
     * `LocaleManager` writes it; `OpenDashApplication` reads it
     * on startup and applies it via `AppCompatDelegate.setApplicationLocales`.
     */
    val APP_LOCALE_TAG = stringPreferencesKey("app_locale_tag")

    /**
     * User-chosen default news feed for the Home dashboard headlines card
     * and the fallback used by `get_news` when the utterance doesn't name
     * a specific feed. Value is an RSS/Atom URL; empty / unset means
     * "fall back to the built-in NHK General feed" (backward-compat with
     * pre-setting installs). Complements [DEFAULT_LOCATION].
     */
    val DEFAULT_NEWS_FEED_URL = stringPreferencesKey("default_news_feed_url")

    /**
     * Optional human label for the currently selected news feed —
     * rendered next to the picker row ("NHK 社会" / "BBC Top Stories")
     * so the UI doesn't need to re-parse the feed or resolve the bundled
     * catalog just to show the user's current choice. Empty / unset means
     * "fall back to the bundled label that matches DEFAULT_NEWS_FEED_URL,
     * or the localized Default label if nothing matches".
     */
    val DEFAULT_NEWS_FEED_LABEL = stringPreferencesKey("default_news_feed_label")
}
