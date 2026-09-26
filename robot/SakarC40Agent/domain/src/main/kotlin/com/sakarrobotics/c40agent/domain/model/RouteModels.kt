package com.sakarrobotics.c40agent.domain.model

enum class RouteRecordingState { IDLE, RECORDING, PAUSED }

/**
 * A push-taught route. The Peanut SDK exposes no teach/record API (see
 * COMPATIBILITY_REPORT.md) - recording is a LOCAL capture of elapsed time
 * and (if available) periodic position reads from [RobotSensorsRepository],
 * never a fabricated path. [pointCount] reflects only how many position
 * samples were actually captured; it is 0 whenever position is UNAVAILABLE.
 */
data class RouteRecord(
    val id: String,
    val name: String,
    val createdAtMillis: Long,
    val durationSeconds: Long,
    val pointCount: Int,
    val capability: Capability
)
