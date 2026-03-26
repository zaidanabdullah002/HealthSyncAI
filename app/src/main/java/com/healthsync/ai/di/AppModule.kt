package com.healthsync.ai.di

import android.content.Context
import com.healthsync.ai.data.AppDatabase
import com.healthsync.ai.repository.HealthRepository
import com.healthsync.ai.model.HealthEventDao
import com.healthsync.ai.service.HealthApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getInstance(context)
    }

    @Provides
    fun provideHealthEventDao(database: AppDatabase): HealthEventDao {
        return database.healthEventDao()
    }

    @Provides
    @Singleton
    fun provideHealthApiService(): HealthApiService {
        return HealthApiService.create()
    }

    @Provides
    @Singleton
    fun provideHealthRepository(
        dao: HealthEventDao,
        api: HealthApiService
    ): HealthRepository {
        return HealthRepository(dao, api)
    }
}
