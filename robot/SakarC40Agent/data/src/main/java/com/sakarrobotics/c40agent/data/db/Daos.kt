package com.sakarrobotics.c40agent.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduleTaskDao {
    @Query("SELECT * FROM schedule_tasks ORDER BY startTime ASC")
    fun observeAll(): Flow<List<ScheduleTaskEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ScheduleTaskEntity)

    @Query("DELETE FROM schedule_tasks WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE schedule_tasks SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean)

    @Query("DELETE FROM schedule_tasks")
    suspend fun deleteAll()
}

@Dao
interface ConsumableDao {
    @Query("SELECT * FROM consumables ORDER BY displayName ASC")
    fun observeAll(): Flow<List<ConsumableEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(entities: List<ConsumableEntity>)

    @Update
    suspend fun update(entity: ConsumableEntity)

    @Query("SELECT * FROM consumables WHERE id = :id")
    suspend fun get(id: String): ConsumableEntity?

    @Query("DELETE FROM consumables")
    suspend fun deleteAll()
}

@Dao
interface RouteDao {
    @Query("SELECT * FROM routes ORDER BY createdAtMillis DESC")
    fun observeAll(): Flow<List<RouteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: RouteEntity)

    @Query("UPDATE routes SET name = :name WHERE id = :id")
    suspend fun rename(id: String, name: String)

    @Query("DELETE FROM routes WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM routes")
    suspend fun deleteAll()
}

@Dao
interface CleaningZoneDao {
    @Query("SELECT * FROM cleaning_zones WHERE mapId = :mapId")
    suspend fun forMap(mapId: String): List<CleaningZoneEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CleaningZoneEntity)

    @Query("DELETE FROM cleaning_zones WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM cleaning_zones")
    suspend fun deleteAll()
}
