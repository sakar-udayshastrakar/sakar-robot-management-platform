package com.sakarrobotics.c40agent.domain.repository

import com.sakarrobotics.c40agent.domain.model.RobotCapabilityResult
import com.sakarrobotics.c40agent.domain.model.RobotTelemetrySnapshot
import kotlinx.coroutines.flow.Flow

/**
 * Gateway-level telemetry capability - a thin re-projection of already-real data from
 * [RobotConnectionRepository]/[RobotBatteryRepository], not a new data source. Exists so a future
 * consumer (e.g. a Sakar Installation Assistant dashboard) has one capability to observe instead of
 * composing several repositories itself. Its implementation must only ever tag a snapshot
 * [com.sakarrobotics.c40agent.domain.model.RobotCapabilityStatus.REAL] when every field inside it
 * is itself real - never partially fabricated.
 */
interface RobotTelemetryRepository {
    val snapshot: Flow<RobotCapabilityResult<RobotTelemetrySnapshot>>
}
