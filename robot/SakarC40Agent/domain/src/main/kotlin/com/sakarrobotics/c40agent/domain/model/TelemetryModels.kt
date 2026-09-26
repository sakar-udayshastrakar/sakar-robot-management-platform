package com.sakarrobotics.c40agent.domain.model

/**
 * A unified, UI/gateway-facing telemetry snapshot - deliberately its own, minimal domain model
 * rather than reusing [com.sakarrobotics.c40agent.telemetry.RuntimeSnapshot] directly, since that
 * type mirrors the Peanut SDK's own `RuntimeInfo` wire shape for the `:api` MQTT publisher, a
 * different concern from a gateway-level "what does the operator/future-Installation-Assistant UI
 * need to know right now" summary. Every field here is sourced from already-real repositories
 * ([com.sakarrobotics.c40agent.domain.repository.RobotBatteryRepository],
 * [com.sakarrobotics.c40agent.domain.repository.RobotConnectionRepository]) - nothing here is a new
 * data source, only a re-projection of existing real data through one seam.
 */
data class RobotTelemetrySnapshot(
    val linkState: RobotLinkState,
    val batteryPercentage: Int?,
    val isCharging: Boolean,
    val lastUpdatedAtMillis: Long
)
