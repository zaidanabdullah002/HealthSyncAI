package com.healthsync.ai.model

data class SyncRequest(
    val events: List<HealthEvent>
)
