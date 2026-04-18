package com.opendash.app.ui.settings.routines

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opendash.app.assistant.routine.Routine
import com.opendash.app.assistant.routine.RoutineRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RoutinesViewModel @Inject constructor(
    private val repository: RoutineRepository
) : ViewModel() {

    data class UiState(
        val routines: List<Routine> = emptyList(),
        val loading: Boolean = true
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val routines = repository.all()
            _state.value = UiState(routines = routines, loading = false)
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            repository.delete(id)
            refresh()
        }
    }
}
