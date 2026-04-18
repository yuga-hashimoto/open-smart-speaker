package com.opendash.app.ui.settings.news

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opendash.app.data.preferences.AppPreferences
import com.opendash.app.data.preferences.PreferenceKeys
import com.opendash.app.tool.info.BundledFeed
import com.opendash.app.tool.info.BundledNewsFeeds
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs the "News feed" row in the Settings screen. Reads the persisted
 * feed URL + human label from [AppPreferences] so the picker row renders
 * the current selection reactively; mutation goes through [applyBundled]
 * / [applyCustom] which persist both the URL and a UI-ready label.
 *
 * Mirrors [com.opendash.app.ui.settings.locale.LocaleSettingsViewModel]
 * exactly — a small dedicated ViewModel is preferable to bolting more
 * methods onto the already-large SettingsViewModel graph so the row can
 * be unit-tested in isolation and injected via `hiltViewModel()` without
 * pulling the full settings dependency tree.
 */
@HiltViewModel
class NewsFeedSettingsViewModel @Inject constructor(
    private val appPreferences: AppPreferences
) : ViewModel() {

    /** Full catalog of bundled feeds rendered by the picker. */
    val bundled: List<BundledFeed> = BundledNewsFeeds.ALL

    /**
     * Current feed URL (`""` == unset — dashboard falls back to NHK
     * General for backward compat). Exposed as a hot StateFlow so the
     * picker label re-renders when the value changes elsewhere (e.g.
     * voice command future work).
     */
    val currentFeedUrl: StateFlow<String> =
        appPreferences.observe(PreferenceKeys.DEFAULT_NEWS_FEED_URL)
            .map { it ?: "" }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000L),
                initialValue = ""
            )

    /**
     * Human-readable label persisted alongside the URL. Empty when unset
     * — the UI falls back to a localized "Default" string so we don't
     * have to resolve the bundled catalog synchronously.
     */
    val currentLabel: StateFlow<String> =
        appPreferences.observe(PreferenceKeys.DEFAULT_NEWS_FEED_LABEL)
            .map { it ?: "" }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000L),
                initialValue = ""
            )

    /**
     * Apply a bundled feed by value. Persists both URL and a UI-ready
     * label (the picker resolves the label from `labelResId` at call
     * time and passes it here so the ViewModel stays Context-free).
     */
    fun applyBundled(feed: BundledFeed, label: String) {
        viewModelScope.launch {
            appPreferences.set(PreferenceKeys.DEFAULT_NEWS_FEED_URL, feed.url)
            appPreferences.set(PreferenceKeys.DEFAULT_NEWS_FEED_LABEL, label)
        }
    }

    /**
     * Apply a custom RSS URL supplied by the user. `label` may be empty
     * — the picker row renders a localized "Custom feed" placeholder in
     * that case.
     */
    fun applyCustom(url: String, label: String) {
        viewModelScope.launch {
            appPreferences.set(PreferenceKeys.DEFAULT_NEWS_FEED_URL, url.trim())
            appPreferences.set(PreferenceKeys.DEFAULT_NEWS_FEED_LABEL, label.trim())
        }
    }

    /**
     * Reset to the built-in default (NHK General). Clears both keys so
     * the backward-compat fallback in [com.opendash.app.ui.home.DefaultOnlineBriefingSource]
     * takes over.
     */
    fun resetToDefault() {
        viewModelScope.launch {
            appPreferences.remove(PreferenceKeys.DEFAULT_NEWS_FEED_URL)
            appPreferences.remove(PreferenceKeys.DEFAULT_NEWS_FEED_LABEL)
        }
    }
}
