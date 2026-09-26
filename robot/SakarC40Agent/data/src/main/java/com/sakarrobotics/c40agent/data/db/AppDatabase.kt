package com.sakarrobotics.c40agent.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ScheduleTaskEntity::class, ConsumableEntity::class, RouteEntity::class, CleaningZoneEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun scheduleTaskDao(): ScheduleTaskDao
    abstract fun consumableDao(): ConsumableDao
    abstract fun routeDao(): RouteDao
    abstract fun cleaningZoneDao(): CleaningZoneDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "sakar_cleanbot.db"
                ).build().also { instance = it }
            }
    }
}
