package com.sakarrobotics.c40agent.domain.di

import com.sakarrobotics.c40agent.domain.gateway.SakarRobotGateway
import com.sakarrobotics.c40agent.domain.model.ManualDriveBackendMode
import com.sakarrobotics.c40agent.domain.model.SimulatedRobotState
import com.sakarrobotics.c40agent.domain.repository.AuthRepository
import com.sakarrobotics.c40agent.domain.repository.CleaningRepository
import com.sakarrobotics.c40agent.domain.repository.ConsumableRepository
import com.sakarrobotics.c40agent.domain.repository.LocalPreferencesRepository
import com.sakarrobotics.c40agent.domain.repository.LogsRepository
import com.sakarrobotics.c40agent.domain.repository.RobotBatteryRepository
import com.sakarrobotics.c40agent.domain.repository.RobotChargingRepository
import com.sakarrobotics.c40agent.domain.repository.RobotConnectionRepository
import com.sakarrobotics.c40agent.domain.repository.RobotDiagnosticsRepository
import com.sakarrobotics.c40agent.domain.repository.RobotIdentityRepository
import com.sakarrobotics.c40agent.domain.repository.RobotMapRepository
import com.sakarrobotics.c40agent.domain.repository.RobotNavigationRepository
import com.sakarrobotics.c40agent.domain.repository.RobotSensorsRepository
import com.sakarrobotics.c40agent.domain.repository.RobotWorkstationRepository
import com.sakarrobotics.c40agent.domain.repository.RouteRepository
import com.sakarrobotics.c40agent.domain.repository.ScheduleRepository
import com.sakarrobotics.c40agent.domain.repository.SystemMaintenanceRepository

/**
 * Aggregates every repository the operator UI needs. :operator-ui depends
 * only on this interface (and the model/repository/usecase types it
 * references) - never on :data, :robot, :sdk, :navigation or :charging.
 * The real implementation, [DefaultAppContainer], lives in :data and is
 * constructed exactly once by SakarC40Application (:app), which is the
 * only place all of :robot, :data and :operator-ui meet.
 *
 * This is deliberately a plain manual-DI container (no Dagger/Hilt): the
 * dependency graph here is small and static for the lifetime of the
 * process, so a generated graph would add annotation-processor build
 * complexity without a matching benefit.
 */
interface AppContainer {
    val connectionRepository: RobotConnectionRepository
    val batteryRepository: RobotBatteryRepository
    val chargingRepository: RobotChargingRepository
    val navigationRepository: RobotNavigationRepository
    /** Which backend Manual Drive's jog() actually drives - see RobotMotionController (:data). */
    val manualDriveBackendMode: ManualDriveBackendMode
    /** Live telemetry from the virtual robot. Only updated while manualDriveBackendMode == SIMULATED. */
    val simulatedRobotState: kotlinx.coroutines.flow.StateFlow<SimulatedRobotState>
    val sensorsRepository: RobotSensorsRepository
    val diagnosticsRepository: RobotDiagnosticsRepository
    val identityRepository: RobotIdentityRepository
    val mapRepository: RobotMapRepository
    val workstationRepository: RobotWorkstationRepository

    val cleaningRepository: CleaningRepository
    val scheduleRepository: ScheduleRepository
    val consumableRepository: ConsumableRepository
    val routeRepository: RouteRepository
    val logsRepository: LogsRepository
    val authRepository: AuthRepository
    val localPreferencesRepository: LocalPreferencesRepository
    val systemMaintenanceRepository: SystemMaintenanceRepository

    /**
     * Architecture-preparation seam (see docs/architecture/SAKAR_ROBOT_SOFTWARE_ARCHITECTURE.md) -
     * forwards to the same repository instances already exposed individually above; not a second
     * source of truth. Nothing existing depends on this yet - it exists for future use cases (this
     * app's own, and a future SakarInstallationAssistant's) to depend on instead of this whole
     * container when they only need robot-hardware-facing capabilities.
     */
    val robotGateway: SakarRobotGateway
}

/** Implemented by SakarC40Application (Java) so :operator-ui can reach the container without depending on :data. */
interface AppContainerHolder {
    val appContainer: AppContainer
}
