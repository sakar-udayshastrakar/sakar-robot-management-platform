package com.sakarrobotics.c40agent.data.repository

import com.sakarrobotics.c40agent.domain.model.Capability
import com.sakarrobotics.c40agent.domain.model.RobotCapabilityResult
import com.sakarrobotics.c40agent.domain.model.RobotCapabilityStatus
import com.sakarrobotics.c40agent.domain.model.RobotTelemetrySnapshot
import com.sakarrobotics.c40agent.domain.repository.RobotBatteryRepository
import com.sakarrobotics.c40agent.domain.repository.RobotConnectionRepository
import com.sakarrobotics.c40agent.domain.repository.RobotTelemetryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * REAL - a re-projection of [RobotConnectionRepository.linkState] and [RobotBatteryRepository.battery],
 * both already real bridges over [com.sakarrobotics.c40agent.robot.C40RobotController]. This class
 * introduces no new data source; it only combines two existing real Flows into one gateway-level
 * snapshot. If either source's own reading is ever simulated/unavailable, this must not silently
 * report [RobotCapabilityStatus.REAL] regardless - see the `capability` check on the battery
 * reading below.
 */
class RobotTelemetryRepositoryImpl(
    connectionRepository: RobotConnectionRepository,
    batteryRepository: RobotBatteryRepository
) : RobotTelemetryRepository {

    override val snapshot: Flow<RobotCapabilityResult<RobotTelemetrySnapshot>> =
        combine(connectionRepository.linkState, batteryRepository.battery) { linkState, battery ->
            if (battery.capability != Capability.REAL) {
                return@combine RobotCapabilityResult.Unavailable(
                    status = RobotCapabilityStatus.NOT_AVAILABLE,
                    reason = "Underlying battery reading is not REAL (capability=${battery.capability})."
                )
            }
            RobotCapabilityResult.Available(
                value = RobotTelemetrySnapshot(
                    linkState = linkState,
                    batteryPercentage = battery.percentage,
                    isCharging = battery.isCharging,
                    lastUpdatedAtMillis = System.currentTimeMillis()
                ),
                status = RobotCapabilityStatus.REAL
            )
        }
}
