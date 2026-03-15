package com.healthsync.ai.model

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
/*
* - Insert a health event
- Get all unsynced events as a Flow
- Get the max value for a given type (your CRDT logic)
- Mark a list of events as synced after a successful backend call
* */

@Dao
interface HealthEventDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(healthEvent: HealthEvent)

    @Query("SELECT * from health_events")
    fun getAllEvents() : Flow<List<HealthEvent>>

    @Query("SELECT * from health_events where isSynced = 0")
    fun getUnsyncedData() : Flow<List<HealthEvent>>

    @Query("SELECT MAX(value) from health_events where type = :type")
    suspend fun getMaxValue(type : String) : Double?

    @Query("UPDATE health_events SET isSynced = 1 WHERE id IN (:ids)")
    suspend fun markUnsyncedDataSynced(ids : List<String>)

}