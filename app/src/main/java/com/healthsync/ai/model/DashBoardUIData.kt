package com.healthsync.ai.model

data class DashBoardUIData(val steps: Double = 0.0,
                           val activeTime: Double = 0.0,
                           val calories: Double = 0.0,
                           val isLoading: Boolean,
                           val errorMessage: String? = null)

fun List<HealthEvent>.toDashboardUiState(): DashBoardUIData {
    return DashBoardUIData(
        steps = filter { it.type == "STEPS" }
            .maxOfOrNull { it.value } ?: 0.0,
        activeTime = filter { it.type == "ACTIVE_TIME" }
            .maxOfOrNull { it.value } ?: 0.0,
        calories = filter { it.type == "CALORIES" }
            .maxOfOrNull { it.value } ?: 0.0,
        isLoading = false
    )
}