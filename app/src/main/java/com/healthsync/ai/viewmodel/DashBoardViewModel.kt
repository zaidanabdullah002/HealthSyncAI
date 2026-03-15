// DashBoardViewModel.kt
package com.healthsync.ai.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.healthsync.ai.data.repository.HealthRepository
import com.healthsync.ai.model.DashBoardUIData
import com.healthsync.ai.model.HealthEvent
import com.healthsync.ai.model.toDashboardUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashBoardViewModel @Inject constructor(
    private val repository: HealthRepository
) : ViewModel() {

    // dashboard summary — max across all devices
    val uiState: StateFlow<DashBoardUIData> = repository.getAllEvents()
        .map { it.toDashboardUiState() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = DashBoardUIData(isLoading = true)
        )

    // per-device steps — scales to any number of devices
    val deviceSteps: StateFlow<Map<String, Double>> = repository.getAllEvents()
        .map { events ->
            events
                .filter { it.type == "STEPS" }
                .groupBy { it.deviceId }
                .mapValues { (_, deviceEvents) ->
                    deviceEvents.maxOf { it.value }
                }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyMap()
        )

    fun saveEvent(deviceId: String, type: String, value: Double) {
        viewModelScope.launch {
            repository.logEvent(
                HealthEvent(deviceId = deviceId, type = type, value = value)
            )
        }
    }
}