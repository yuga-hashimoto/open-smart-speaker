package com.opensmarthome.speaker.ui.settings.weather

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opensmarthome.speaker.data.location.CitySearchRepository
import com.opensmarthome.speaker.data.location.CitySuggestion
import com.opensmarthome.speaker.data.preferences.AppPreferences
import com.opensmarthome.speaker.data.preferences.PreferenceKeys
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
 * [com.opensmarthome.speaker.ui.settings.locale.LocaleSettingsViewModel]'s
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
     * Currently persisted default location label. Empty string when
     * unset — the weather provider falls back to its built-in Tokyo
     * default (see [PreferenceKeys.DEFAULT_LOCATION] kdoc).
     */
    val currentLocation: StateFlow<String> =
        preferences.observe(PreferenceKeys.DEFAULT_LOCATION)
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
     * Persist the user's selection to DataStore. Trimmed so the weather
     * tool's [com.opensmarthome.speaker.tool.info.WeatherToolExecutor]
     * `resolveLocation` logic sees the exact label the picker rendered.
     */
    fun applyLocation(label: String) {
        viewModelScope.launch {
            preferences.set(PreferenceKeys.DEFAULT_LOCATION, label.trim())
        }
    }
}
