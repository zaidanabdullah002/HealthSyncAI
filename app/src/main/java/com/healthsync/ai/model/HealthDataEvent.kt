package com.healthsync.ai.model


import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "health_events")
data class HealthEvent(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val deviceId: String,      // "phone-001" or "watch-001"
    val type: String,          // "STEPS", "ACTIVE_TIME", "CALORIES"
    val value: Double,         // 5000.0
    val timestamp: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false   // has this reached the backend yet?
)