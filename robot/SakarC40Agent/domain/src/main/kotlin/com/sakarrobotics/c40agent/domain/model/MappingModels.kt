package com.sakarrobotics.c40agent.domain.model

/**
 * Live mapping/SLAM session state - distinct from the existing, already-real [MapSummary] /
 * `RobotMapRepository` (:domain), which only covers file-level map management (list/download/
 * deploy already-built maps via the public Peanut SDK). This enum describes a *live* mapping
 * session (start/stop/build), which has no confirmed SDK or ROS API yet - see
 * docs/engineering/04_SLAM_INVESTIGATION_STATUS.md.
 */
enum class MappingSessionState { IDLE, MAPPING, PAUSED, ERROR }

/** Robot pose in the map frame - populated only once a real localization source exists. */
data class RobotPose(
    val x: Double,
    val y: Double,
    val headingRadians: Double
)

/** Map metadata - populated only once a real mapping/SLAM source exists. */
data class RobotMapMetadata(
    val mapId: String,
    val name: String,
    val createdAtMillis: Long?,
    val resolutionMetersPerPixel: Double?,
    val widthPixels: Int?,
    val heightPixels: Int?
)
