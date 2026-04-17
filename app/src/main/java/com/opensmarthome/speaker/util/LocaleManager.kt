package com.opensmarthome.speaker.util

import android.app.LocaleManager as SystemLocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import com.opensmarthome.speaker.data.preferences.AppPreferences
import com.opensmarthome.speaker.data.preferences.PreferenceKeys
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Applies a user-selected UI locale on top of the system default.
 *
 * Pattern mirrors openclaw-assistant's `applySavedAppLocale`: a BCP-47
 * tag persisted in [AppPreferences], empty string means "follow system".
 * Applied at app startup (call [applySaved]) and whenever the user
 * changes the selection (call [apply] with the new tag — it persists
 * *and* takes effect without a restart).
 *
 * On Android 13 (API 33) and newer we route through the platform
 * [SystemLocaleManager], which gives us per-app language preferences
 * respected by the system (Settings → System → Languages → OpenSmartSpeaker
 * reflects the same choice) and auto-applied to every Activity / Service
 * the app starts.
 *
 * On API 28-32 the platform LocaleManager doesn't exist. Users on those
 * devices fall back to the system locale — [isOverrideSupported] returns
 * false and the Settings UI should hide the picker there. Supporting
 * those older versions would require the AppCompat dep, and the project
 * rule is "no new dependencies without asking"; Android 13+ covers the
 * tablet form factor we target.
 */
@Singleton
class LocaleManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: AppPreferences
) {

    /**
     * True when the platform supports per-app language override —
     * Android 13 (API 33) and above. On older versions [apply] is a
     * no-op (except for persisting the user's preference for a future
     * upgrade).
     */
    val isOverrideSupported: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    /**
     * Bundled locales the user can pick from. The empty tag means
     * "follow system". Keys are BCP-47 language tags; labels render in
     * their own language so the picker is self-descriptive regardless
     * of the user's current UI language.
     */
    data class Option(val tag: String, val label: String)

    val options: List<Option> = listOf(
        Option("", "System default"),
        Option("en", "English"),
        Option("ja", "日本語"),
        Option("es", "Español"),
        Option("fr", "Français"),
        Option("de", "Deutsch"),
        Option("zh-CN", "简体中文")
    )

    /**
     * Persist [tag] as the user's preference AND push it to the system
     * LocaleManager so the change is visible immediately. Pass `""` to
     * clear the override (follow-system).
     */
    suspend fun apply(tag: String) {
        val trimmed = tag.trim()
        preferences.set(PreferenceKeys.APP_LOCALE_TAG, trimmed)
        pushToSystem(trimmed)
    }

    /**
     * Called at Application.onCreate — reads the saved tag and pushes
     * it to the platform. Safe to call on every start; empty saved tag
     * produces an empty LocaleList which the system reads as "inherit".
     */
    suspend fun applySaved() {
        val saved = preferences.observe(PreferenceKeys.APP_LOCALE_TAG).first() ?: ""
        pushToSystem(saved)
    }

    /**
     * Synchronous read of the persisted tag. Provided so the Settings
     * UI can render the currently-selected row without stateful Flow
     * collection; mutation still goes through [apply].
     */
    suspend fun currentTag(): String =
        preferences.observe(PreferenceKeys.APP_LOCALE_TAG).first() ?: ""

    private fun pushToSystem(tag: String) {
        if (!isOverrideSupported) {
            Timber.d("LocaleManager: override unsupported below API 33, persist-only")
            return
        }
        try {
            val platform = context.getSystemService(SystemLocaleManager::class.java)
            if (platform == null) {
                Timber.w("LocaleManager: system service unavailable, skipping push")
                return
            }
            platform.applicationLocales = if (tag.isBlank()) {
                LocaleList.getEmptyLocaleList()
            } else {
                LocaleList.forLanguageTags(tag)
            }
            Timber.d("LocaleManager: applied locale tag=$tag")
        } catch (e: Throwable) {
            Timber.w(e, "LocaleManager: failed to apply locale tag=$tag")
        }
    }
}
