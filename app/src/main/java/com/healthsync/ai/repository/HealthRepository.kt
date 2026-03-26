package com.healthsync.ai.repository

import com.healthsync.ai.model.HealthEvent
import com.healthsync.ai.model.HealthEventDao
import com.healthsync.ai.model.HealthSummary
import com.healthsync.ai.model.SyncRequest
import com.healthsync.ai.service.HealthApiService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HealthRepository @Inject constructor(
    private val dao: HealthEventDao,
    private val api: HealthApiService
) {

    private fun getTodayRange(): Pair<Long, Long> {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val start = calendar.timeInMillis
        val end = start + 24L * 60 * 60 * 1000
        return Pair(start, end)
    }

    // today's events — for dashboard display
    fun getTodayEvents(): Flow<List<HealthEvent>> {
        val (start, end) = getTodayRange()
        return dao.getEventsByDay(start, end)
    }

    // all events — kept for history if needed later
    fun getAllEvents(): Flow<List<HealthEvent>> {
        return dao.getAllEvents()
    }

    suspend fun logEvent(event: HealthEvent) {
        dao.insert(event)
    }

    suspend fun syncEvents() {
        val (start, end) = getTodayRange()
        val unsyncedEvents = dao.getUnsyncedByDay(start, end).first()
        if (unsyncedEvents.isEmpty()) return
        val response = api.syncEvents(SyncRequest(events = unsyncedEvents))
        if (!response.isSuccessful) throw Exception("Sync failed: ${response.code()}")
        dao.markUnsyncedDataSynced(unsyncedEvents.map { it.id })
    }

    suspend fun getGlobalSummary(): HealthSummary {
        val (start, end) = getTodayRange()
        return api.getGlobalSummary(start = start, end = end)
    }

    suspend fun getDeviceSummary(deviceId: String): HealthSummary {
        return api.getSummary(deviceId)
    }

    suspend fun removeDevice(deviceId: String) {
        dao.removeDevice(deviceId)
    }

    suspend fun clearAllData() {
        dao.clearAllData()
    }
}