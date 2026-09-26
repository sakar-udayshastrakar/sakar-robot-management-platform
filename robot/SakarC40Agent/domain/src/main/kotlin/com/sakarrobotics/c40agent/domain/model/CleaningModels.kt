package com.sakarrobotics.c40agent.domain.model

enum class CleaningMode { SWEEP, SWEEP_MOP, WATER_SUCTION, SWEEP_VACUUM, SWEEP_PUSH }

enum class CleaningIntensity { GENTLE, STANDARD, POWERFUL }

/** A named area a map can be divided into for area-selective cleaning. Drawn/persisted locally - see MapRepository. */
data class CleaningZone(
    val id: String,
    val mapId: String,
    val name: String,
    val polygon: List<PointXY>,
    val floor: Int
)

data class PointXY(val x: Float, val y: Float)

enum class CleaningRunState { IDLE, CLEANING, PAUSED, RETURNING_TO_DOCK, COMPLETED, ERROR }

data class CleaningSession(
    val state: CleaningRunState,
    val zoneIds: List<String>,
    val mode: CleaningMode,
    val intensity: CleaningIntensity,
    val progressPercent: Int,
    val elapsedSeconds: Long,
    val estimatedRemainingSeconds: Long,
    val cyclesRequested: Int,
    val cyclesCompleted: Int,
    val capability: Capability
)

enum class ManualDriveDirection { FORWARD, REVERSE, LEFT, RIGHT, STOP }
