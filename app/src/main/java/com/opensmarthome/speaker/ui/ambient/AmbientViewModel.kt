package com.opensmarthome.speaker.ui.ambient

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opensmarthome.speaker.homeassistant.cache.EntityCache
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AmbientViewModel @Inject constructor(
    private val entityCache: EntityCache
) : ViewModel() {

    private val _weatherState = MutableStateFlow("")
    val weatherState: StateFlow<String> = _weatherState.asStateFlow()

    private val _temperature = MutableStateFlow("")
    val temperature: StateFlow<String> = _temperature.asStateFlow()

    private val _humidity = MutableStateFlow("")
    val humidity: StateFlow<String> = _humidity.asStateFlow()

    init {
        viewModelScope.launch {
            entityCache.entities.collect { entities ->
                val weather = entities.values.firstOrNull { it.domain == "weather" }
                if (weather != null) {
                    _weatherState.value = weather.state
                    _temperature.value = (weather.attributes["temperature"] as? Number)?.toString() ?: ""
                    _humidity.value = (weather.attributes["humidity"] as? Number)?.toString() ?: ""
                }
            }
        }
    }
}
