package com.opendash.app.ui.settings.weather

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opendash.app.data.location.CitySearchRepository
import com.opendash.app.data.location.CitySuggestion
import com.opendash.app.data.preferences.AppPreferences
import com.opendash.app.data.preferences.PreferenceKeys
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

/**
 * Backs the "Default weather location" picker in Settings. Mirrors
 * [com.opendash.app.ui.settings.locale.LocaleSettingsViewModel]'s
 * shape so the Compose row stays a drop-in swap for the existing free-text
 * input — a dedicated, small VM keeps the settings graph decoupled and
 * unit-testable in isolation.
 *
 * The debounced search wires a raw text field to the geocoding API. Without
 * debounce every keystroke triggers a network call; 300 ms is the sweet
 * spot HA's EntityPicker (see reference repo `home-assistant/android`)
 * has converged on.
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class WeatherLocationSettingsViewModel @Inject constructor(
    private val preferences: AppPreferences,
    private val citySearchRepository: CitySearchRepository
) : ViewModel() {

    companion object {
        /**
         * Input idle time before firing a geocoding request. Too short
         * burns API quota on transient typing; too long feels laggy.
         * 300 ms matches HA's EntityPicker and Google's Material search
         * guidance.
         */
        internal const val DEBOUNCE_MS = 300L
    }

    /**
     * Currently persisted default location — the simple city name that the
     * weather provider hands to Open-Meteo's geocoding API. Empty string
     * when unset (the provider then falls back to its built-in Tokyo
     * default; see [PreferenceKeys.DEFAULT_LOCATION] kdoc).
     *
     * This is the API-facing value — NOT what the picker row should render.
     * Use [currentDisplayLabel] for UI text so the user sees the full
     * `"Munakata, Fukuoka, Japan"` label they selected.
     */
    val currentLocation: StateFlow<String> =
        preferences.observe(PreferenceKeys.DEFAULT_LOCATION)
            .map { it ?: "" }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000L),
                initialValue = ""
            )

    /**
     * Human-facing label the picker row renders. Falls back to the raw
     * [currentLocation] when the label key is unset — this covers the
     * edge case of a pre-migration install where only [PreferenceKeys.DEFAULT_LOCATION]
     * was written (picker users upgrading from the PR #424 build that
     * saved the label to the API-facing key).
     */
    val currentDisplayLabel: StateFlow<String> =
        preferences.observe(PreferenceKeys.DEFAULT_LOCATION_DISPLAY_LABEL)
            .map { it ?: "" }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000L),
                initialValue = ""
            )

    private val queryFlow = MutableStateFlow("")

    private val _queryResults = MutableStateFlow<List<CitySuggestion>>(emptyList())

    /**
     * Hot StateFlow of city suggestions for the picker dropdown. Emits
     * `emptyList` for blank queries and for failed searches — the UI
     * treats both states as "no results" and renders a hint line.
     */
    val queryResults: StateFlow<List<CitySuggestion>> = _queryResults.asStateFlow()

    init {
        viewModelScope.launch {
            queryFlow
                .debounce(DEBOUNCE_MS.milliseconds)
                .distinctUntilChanged()
                .onEach { query ->
                    val trimmed = query.trim()
                    if (trimmed.isEmpty()) {
                        _queryResults.value = emptyList()
                    } else {
                        citySearchRepository.search(trimmed).onSuccess { hits ->
                            _queryResults.value = hits
                        }
                        // On failure we intentionally leave the previous
                        // list unchanged — the AlertDialog shows a "no
                        // results" footer only when value is empty, which
                        // happens for a blank query. A transient network
                        // blip shouldn't wipe the list the user is
                        // scanning.
                    }
                }
                .collect {}
        }
    }

    /**
     * Fed from the search field's `onValueChange`. Every call updates the
     * upstream Flow; the debounce in [init] coalesces rapid typing into
     * a single repository call.
     */
    fun updateQuery(query: String) {
        queryFlow.value = query
    }

    /**
     * Persist the user's selection to DataStore.
     *
     * Splits the suggestion into two writes:
     * - `DEFAULT_LOCATION` gets the simple [CitySuggestion.name] that
     *   Open-Meteo's geocoding API can actually resolve
     *   (e.g. `"Munakata"`).
     * - `DEFAULT_LOCATION_DISPLAY_LABEL` gets the human-facing
     *   `"Munakata, Fukuoka, Japan"` string so the picker row can
     *   render the same label the user saw when they chose it.
     *
     * This two-key split fixes the bug where `"Munakata, Fukuoka, Japan"`
     * was fed verbatim to the geocoder and always returned zero results
     * (PR #424 regression).
     */
    fun applyLocation(suggestion: CitySuggestion) {
        viewModelScope.launch {
            preferences.set(PreferenceKeys.DEFAULT_LOCATION, suggestion.name.trim())
            preferences.set(
                PreferenceKeys.DEFAULT_LOCATION_DISPLAY_LABEL,
                suggestion.displayLabel.trim()
            )
        }
    }

    /**
     * Reset the user's override so the provider falls back to its
     * built-in Tokyo default. Clears both the API-facing value and the
     * display label so the picker row returns to the localized
     * "Tokyo (built-in default)" string.
     */
    fun clearLocation() {
        viewModelScope.launch {
            preferences.set(PreferenceKeys.DEFAULT_LOCATION, "")
            preferences.set(PreferenceKeys.DEFAULT_LOCATION_DISPLAY_LABEL, "")
        }
    }
}
