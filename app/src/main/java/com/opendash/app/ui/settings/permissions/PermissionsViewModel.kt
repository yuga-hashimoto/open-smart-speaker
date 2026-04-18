package com.opendash.app.ui.settings.permissions

import androidx.lifecycle.ViewModel
import com.opendash.app.permission.PermissionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class PermissionsViewModel @Inject constructor(
    private val repository: PermissionRepository
) : ViewModel() {

    data class UiState(
        val rows: List<PermissionRepository.Row> = emptyList(),
        val ungrantedCount: Int = 0
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        val rows = repository.rows()
        _state.value = UiState(rows = rows, ungrantedCount = rows.count { !it.granted })
    }
}
