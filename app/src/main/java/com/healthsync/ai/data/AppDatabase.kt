package com.healthsync.ai.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.healthsync.ai.model.HealthEvent
import com.healthsync.ai.model.HealthEventDao

@Database(entities = [HealthEvent::class], version = 1)
abstract class AppDatabase : RoomDatabase(){

    abstract fun healthEventDao() : HealthEventDao

    companion object{
        const val DATABASE_NAME = "health-event-db"
        @Volatile
        private var instance : AppDatabase? = null
        fun getInstance(context : Context) : AppDatabase{
            return instance ?: synchronized(this){
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                ).build().also { instance = it }
            }
        }
    }


}