package com.opendash.app.ui.settings.locale

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opendash.app.data.preferences.AppPreferences
import com.opendash.app.data.preferences.PreferenceKeys
import com.opendash.app.util.LocaleManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs the "Language" row in the Settings screen. Reads the persisted
 * BCP-47 tag from [AppPreferences] so the row renders the current
 * selection label reactively; mutation goes through [LocaleManager.apply]
 * which both persists the new tag AND pushes it to the platform locale
 * service (no restart required on Android 13+).
 *
 * Kept as a dedicated small ViewModel — rather than a new method on the
 * already-large [com.opendash.app.ui.settings.SettingsViewModel]
 * — so the locale row can be unit-tested in isolation and so the picker
 * row in Compose can use its own `hiltViewModel()` without pulling the
 * full settings graph.
 */
@HiltViewModel
class LocaleSettingsViewModel @Inject constructor(
    private val localeManager: LocaleManager,
    appPreferences: AppPreferences
) : ViewModel() {

    /** Whether the platform supports per-app locale override (API 33+). */
    val isOverrideSupported: Boolean = localeManager.isOverrideSupported

    /** Bundled locales the picker renders as options. */
    val options: List<LocaleManager.Option> = localeManager.options

    /**
     * Current BCP-47 tag (`""` == follow-system). Exposed as a hot
     * StateFlow so the row label can re-render when the value changes —
     * e.g. when the user picks a new locale from this same screen.
     */
    val currentTag: StateFlow<String> =
        appPreferences.observe(PreferenceKeys.APP_LOCALE_TAG)
            .map { it ?: "" }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000L),
                initialValue = ""
            )

    /**
     * Apply the user's selection. Fire-and-forget — [LocaleManager.apply]
     * both persists the preference and pushes it to the platform locale
     * service. On API 28-32 it's persist-only (see [isOverrideSupported]).
     */
    fun applyLocale(tag: String) {
        viewModelScope.launch {
            localeManager.apply(tag)
        }
    }
}
