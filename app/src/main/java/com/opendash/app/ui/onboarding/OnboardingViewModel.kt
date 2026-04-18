package com.opendash.app.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opendash.app.data.preferences.AppPreferences
import com.opendash.app.data.preferences.PreferenceKeys
import com.opendash.app.permission.PermissionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives the first-run permission walkthrough. Exposes a flat list of
 * permission rows and a [markCompleted] hook that flips SETUP_COMPLETED so
 * MainActivity moves past onboarding on subsequent launches.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val repository: PermissionRepository,
    private val preferences: AppPreferences
) : ViewModel() {

    data class UiState(
        val rows: List<PermissionRepository.Row> = emptyList(),
        val ungrantedCount: Int = 0
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        // Ensure observe() is wired so downstream code can collect the flag later
        // (we don't block onboarding on the flow value here — the screen always
        // shows until markCompleted is called).
        preferences.observe(PreferenceKeys.SETUP_COMPLETED)
        refresh()
    }

    fun refresh() {
        val rows = repository.rows()
        _state.value = UiState(rows = rows, ungrantedCount = rows.count { !it.granted })
    }

    fun markCompleted() {
        viewModelScope.launch {
            preferences.set(PreferenceKeys.SETUP_COMPLETED, true)
        }
    }
}
