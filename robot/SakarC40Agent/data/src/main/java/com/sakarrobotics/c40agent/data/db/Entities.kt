package com.sakarrobotics.c40agent.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "schedule_tasks")
data class ScheduleTaskEntity(
    @PrimaryKey val id: String,
    val name: String,
    val enabled: Boolean,
    val startTime: String,
    val endTime: String,
    val repeat: String,
    val days: String, // comma-separated Weekday names
    val cleaningCycles: Int,
    val zoneIds: String, // comma-separated
    val mode: String,
    val intensity: String,
    val remoteId: String?,
    val syncState: String
)

@Entity(tableName = "consumables")
data class ConsumableEntity(
    @PrimaryKey val id: String,
    val type: String,
    val displayName: String,
    val lifespanHours: Int,
    val usedHours: Int
)

@Entity(tableName = "routes")
data class RouteEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAtMillis: Long,
    val durationSeconds: Long,
    val pointCount: Int,
    val capability: String
)

@Entity(tableName = "cleaning_zones")
data class CleaningZoneEntity(
    @PrimaryKey val id: String,
    val mapId: String,
    val name: String,
    val polygonCsv: String, // "x1,y1;x2,y2;..."
    val floor: Int
)
