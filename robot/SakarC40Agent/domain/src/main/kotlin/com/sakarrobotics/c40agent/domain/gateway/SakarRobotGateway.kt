package com.sakarrobotics.c40agent.domain.gateway

import com.sakarrobotics.c40agent.domain.repository.CleaningRepository
import com.sakarrobotics.c40agent.domain.repository.RobotChargingRepository
import com.sakarrobotics.c40agent.domain.repository.RobotConnectionRepository
import com.sakarrobotics.c40agent.domain.repository.RobotDiagnosticsRepository
import com.sakarrobotics.c40agent.domain.repository.RobotMapRepository
import com.sakarrobotics.c40agent.domain.repository.RobotMappingRepository
import com.sakarrobotics.c40agent.domain.repository.RobotNavigationRepository
import com.sakarrobotics.c40agent.domain.repository.RobotSensorsRepository
import com.sakarrobotics.c40agent.domain.repository.RobotTelemetryRepository
import com.sakarrobotics.c40agent.domain.repository.RosRobotAdapter

/**
 * The seam future use cases (this app's own, and a future SakarInstallationAssistant's) should
 * depend on instead of the full [com.sakarrobotics.c40agent.domain.di.AppContainer] grab-bag, when
 * they only need robot-hardware-facing capabilities and not purely-local app state (schedules,
 * consumables, logs, auth, local preferences - none of which will ever be "plugged in" to a real
 * ROS/Robot Computer, so they stay out of this narrower interface).
 *
 * This is architecture PREPARATION only: nothing existing is rewired through this gateway yet (no
 * existing ViewModel/use case changes) - see docs/architecture/SAKAR_ROBOT_SOFTWARE_ARCHITECTURE.md
 * §2 for why that is deliberate. [com.sakarrobotics.c40agent.data.gateway.SakarRobotGatewayImpl]
 * (:data) does not construct new repository instances; it forwards to the same ones
 * [com.sakarrobotics.c40agent.domain.di.AppContainer]/`DefaultAppContainer` already construct, so
 * there remains exactly one source of truth for each repository instance.
 *
 * Boundary mapping (see docs/architecture/SAKAR_ROBOT_SOFTWARE_ARCHITECTURE.md §3 for the full
 * rationale, including why "RobotDrive" has no separate interface of its own):
 * - RobotConnection -> [connection] (existing Peanut-SDK link) and [ros] (future ROS/Robot Computer
 *   link - distinct connections to two distinct systems, never conflated)
 * - RobotDrive -> [navigation]'s own `jog(linearVelocity, angularVelocity)` method (manual
 *   low-level movement is already a domain-level capability of [RobotNavigationRepository]; no
 *   duplicate interface is introduced for it)
 * - RobotNavigation -> [navigation]
 * - RobotSensors -> [sensors]
 * - RobotMapping -> [mapping] (new; file-level map management stays at [maps])
 * - RobotCleaning -> [cleaning]
 * - RobotCharging -> [charging]
 * - RobotDiagnostics -> [diagnostics]
 * - RobotTelemetry -> [telemetry] (new)
 */
interface SakarRobotGateway {
    val connection: RobotConnectionRepository
    val ros: RosRobotAdapter
    val navigation: RobotNavigationRepository
    val sensors: RobotSensorsRepository
    val mapping: RobotMappingRepository
    val maps: RobotMapRepository
    val cleaning: CleaningRepository
    val charging: RobotChargingRepository
    val diagnostics: RobotDiagnosticsRepository
    val telemetry: RobotTelemetryRepository
}
