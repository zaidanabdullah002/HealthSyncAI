package com.healthsync.ai.model

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HealthEventDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(healthEvent: HealthEvent)

    @Query("SELECT * FROM health_events")
    fun getAllEvents(): Flow<List<HealthEvent>>

    // today's events — all
    @Query("SELECT * FROM health_events WHERE timestamp >= :start AND timestamp < :end")
    fun getEventsByDay(start: Long, end: Long): Flow<List<HealthEvent>>

    // today's events — unsynced only
    @Query("SELECT * FROM health_events WHERE isSynced = 0 AND timestamp >= :start AND timestamp < :end")
    fun getUnsyncedByDay(start: Long, end: Long): Flow<List<HealthEvent>>

    // all unsynced regardless of date
    @Query("SELECT * FROM health_events WHERE isSynced = 0")
    fun getUnsyncedData(): Flow<List<HealthEvent>>

    @Query("SELECT MAX(value) FROM health_events WHERE type = :type")
    suspend fun getMaxValue(type: String): Double?

    @Query("UPDATE health_events SET isSynced = 1 WHERE id IN (:ids)")
    suspend fun markUnsyncedDataSynced(ids: List<String>)

    @Query("DELETE FROM health_events WHERE deviceId = :deviceId")
    suspend fun removeDevice(deviceId: String)

    @Query("DELETE FROM health_events")
    suspend fun clearAllData()
}