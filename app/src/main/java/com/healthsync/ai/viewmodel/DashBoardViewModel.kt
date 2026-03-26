package com.healthsync.ai.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.healthsync.ai.repository.HealthRepository
import com.healthsync.ai.model.DashBoardUIData
import com.healthsync.ai.model.HealthEvent
import com.healthsync.ai.model.HealthSummary
import com.healthsync.ai.model.toDashboardUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
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

    // backend verified summary
    private val _globalSummary = MutableStateFlow(HealthSummary(0.0, 0.0, 0.0))
    val todayData: StateFlow<HealthSummary> = _globalSummary

    // last synced timestamp
    private val _lastSynced = MutableStateFlow<Long?>(null)
    val lastSynced: StateFlow<Long?> = _lastSynced

    // today's local data — Room source of truth
    val uiState: StateFlow<DashBoardUIData> = repository.getTodayEvents()
        .map { it.toDashboardUiState() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = DashBoardUIData(isLoading = true)
        )

    // per-device metrics for today
    val deviceMetrics: StateFlow<Map<String, HealthSummary>> = repository.getTodayEvents()
        .map { events ->
            events
                .groupBy { it.deviceId }
                .mapValues { (_, deviceEvents) ->
                    HealthSummary(
                        steps = deviceEvents
                            .filter { it.type == "STEPS" }
                            .maxOfOrNull { it.value } ?: 0.0,
                        activeTime = deviceEvents
                            .filter { it.type == "ACTIVE_TIME" }
                            .maxOfOrNull { it.value } ?: 0.0,
                        calories = deviceEvents
                            .filter { it.type == "CALORIES" }
                            .maxOfOrNull { it.value } ?: 0.0
                    )
                }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyMap()
        )

    fun getTodayData() {
        viewModelScope.launch {
            try {
                val summary = repository.getGlobalSummary()
                _globalSummary.value = summary
            } catch (e: Exception) {
                Log.e("HealthSync", "Failed to fetch summary: ${e.message}")
            }
        }
    }

    fun saveEvent(deviceId: String, type: String, value: Double) {
        viewModelScope.launch {
            repository.logEvent(
                HealthEvent(deviceId = deviceId, type = type, value = value)
            )
        }
    }

    fun removeDevice(deviceId: String) {
        viewModelScope.launch {
            repository.removeDevice(deviceId)
        }
    }

    fun triggerSync() {
        viewModelScope.launch {
            try {
                repository.syncEvents()
                _lastSynced.value = System.currentTimeMillis()
                getTodayData()
                Log.d("HealthSync", "Sync successful")
            } catch (e: Exception) {
                Log.e("HealthSync", "Sync failed: ${e.message}")
            }
        }
    }

    fun clearData() {
        viewModelScope.launch {
            repository.clearAllData()
        }
    }
}