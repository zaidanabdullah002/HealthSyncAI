package com.healthsync.ai.service

import com.healthsync.ai.model.HealthSummary
import com.healthsync.ai.model.SyncRequest
import com.healthsync.ai.model.SyncResponse
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface HealthApiService {
    companion object {
        private const val BASE_URL = "http://10.0.2.2:8000/"

        @Volatile
        private var instance: HealthApiService? = null

        fun create(): HealthApiService {
            return instance ?: synchronized(this) {
                instance ?: Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                    .create(HealthApiService::class.java)
                    .also { instance = it }
            }
        }
    }

    @POST("/sync")
    suspend fun syncEvents(
        @Body request: SyncRequest
    ): Response<SyncResponse>

    @GET("/health/{deviceId}/summary")
    suspend fun getSummary(
        @Path("deviceId") deviceId: String
    ): HealthSummary
}
