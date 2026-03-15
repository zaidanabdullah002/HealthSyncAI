package com.healthsync.ai.data.repository

import com.healthsync.ai.model.HealthEvent
import com.healthsync.ai.model.HealthEventDao
import com.healthsync.ai.model.SyncRequest
import com.healthsync.ai.service.HealthApiService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HealthRepository @Inject constructor(
    private val dao: HealthEventDao,
    private val api: HealthApiService
) {

    fun getAllEvents(): Flow<List<HealthEvent>> {
        return dao.getAllEvents()
    }

    fun getUnsyncedEvents(): Flow<List<HealthEvent>> {
        return dao.getUnsyncedData()
    }

    suspend fun logEvent(event: HealthEvent) {
        dao.insert(event)
    }

    suspend fun syncEvents() {
        val unsyncedEvents = dao.getUnsyncedData().first()
        if (unsyncedEvents.isEmpty()) return

        val response = api.syncEvents(SyncRequest(events = unsyncedEvents))
        if (!response.isSuccessful) throw Exception("Sync failed: ${response.code()}")

        dao.markUnsyncedDataSynced(unsyncedEvents.map { it.id })
    }
}
