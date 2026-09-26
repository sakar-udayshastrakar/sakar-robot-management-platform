package com.sakarrobotics.c40agent.data.gateway

import com.sakarrobotics.c40agent.domain.gateway.SakarRobotGateway
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
 * Forwards to repository instances constructed elsewhere (`DefaultAppContainer`, :data) - this
 * class never constructs its own copy of a repository that already exists, so there remains
 * exactly one instance (and therefore one source of truth) for each capability. See
 * [SakarRobotGateway]'s own KDoc for why this seam exists and what it deliberately does not do yet
 * (rewire any existing ViewModel/use case).
 */
class SakarRobotGatewayImpl(
    override val connection: RobotConnectionRepository,
    override val ros: RosRobotAdapter,
    override val navigation: RobotNavigationRepository,
    override val sensors: RobotSensorsRepository,
    override val mapping: RobotMappingRepository,
    override val maps: RobotMapRepository,
    override val cleaning: CleaningRepository,
    override val charging: RobotChargingRepository,
    override val diagnostics: RobotDiagnosticsRepository,
    override val telemetry: RobotTelemetryRepository
) : SakarRobotGateway
