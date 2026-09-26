package com.sakarrobotics.c40agent.data.di

import android.content.Context
import android.util.Log
import com.sakarrobotics.c40agent.data.db.AppDatabase
import com.sakarrobotics.c40agent.data.gateway.SakarRobotGatewayImpl
import com.sakarrobotics.c40agent.data.repository.DataStoreAuthRepository
import com.sakarrobotics.c40agent.data.repository.DataStoreLocalPreferencesRepository
import com.sakarrobotics.c40agent.data.repository.LogsRepositoryImpl
import com.sakarrobotics.c40agent.data.repository.NotAvailableMappingRepository
import com.sakarrobotics.c40agent.data.repository.RealMotorController
import com.sakarrobotics.c40agent.data.repository.RobotBatteryRepositoryImpl
import com.sakarrobotics.c40agent.data.repository.RobotChargingRepositoryImpl
import com.sakarrobotics.c40agent.data.repository.RobotConnectionRepositoryImpl
import com.sakarrobotics.c40agent.data.repository.RobotDiagnosticsRepositoryImpl
import com.sakarrobotics.c40agent.data.repository.RobotIdentityRepositoryImpl
import com.sakarrobotics.c40agent.data.repository.RobotMapRepositoryImpl
import com.sakarrobotics.c40agent.data.repository.RobotMotionController
import com.sakarrobotics.c40agent.data.repository.RobotNavigationRepositoryImpl
import com.sakarrobotics.c40agent.data.repository.RobotSensorsRepositoryImpl
import com.sakarrobotics.c40agent.data.repository.RobotTelemetryRepositoryImpl
import com.sakarrobotics.c40agent.data.repository.RosRobotAdapterStub
import com.sakarrobotics.c40agent.data.repository.SimulatedMotorController
import com.sakarrobotics.c40agent.data.repository.RobotWorkstationRepositoryImpl
import com.sakarrobotics.c40agent.data.repository.RoomConsumableRepository
import com.sakarrobotics.c40agent.data.repository.RoomRouteRepository
import com.sakarrobotics.c40agent.data.repository.RoomScheduleRepository
import com.sakarrobotics.c40agent.data.repository.SimulatedCleaningRepository
import com.sakarrobotics.c40agent.data.repository.SystemMaintenanceRepositoryImpl
import com.sakarrobotics.c40agent.domain.di.AppContainer
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
import com.sakarrobotics.c40agent.domain.repository.RobotMappingRepository
import com.sakarrobotics.c40agent.domain.repository.RobotNavigationRepository
import com.sakarrobotics.c40agent.domain.repository.RobotSensorsRepository
import com.sakarrobotics.c40agent.domain.repository.RobotTelemetryRepository
import com.sakarrobotics.c40agent.domain.repository.RobotWorkstationRepository
import com.sakarrobotics.c40agent.domain.repository.RosRobotAdapter
import com.sakarrobotics.c40agent.domain.repository.RouteRepository
import com.sakarrobotics.c40agent.domain.repository.ScheduleRepository
import com.sakarrobotics.c40agent.domain.repository.SystemMaintenanceRepository
import com.sakarrobotics.c40agent.robot.C40RobotController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * The single real [AppContainer] implementation, constructed once by
 * SakarC40Application.onCreate() (:app) - the one place in this project
 * that :robot (real Peanut SDK bridge), :data (persistence + repository
 * implementations) and :operator-ui (Compose UI, via [AppContainerHolder])
 * all come together. Every repository here is either a real bridge over
 * the existing [C40RobotController], a Room-backed local repository, or an
 * explicitly-labeled simulation - see each repository implementation's own
 * Javadoc/KDoc for which one it is.
 */
class DefaultAppContainer @JvmOverloads constructor(
    context: Context,
    controller: C40RobotController,
    appVersionName: String,
    // Explicit backend selection (Manual Drive Simulator task, Step 6): this build always
    // defaults new construction to SIMULATED so the emulator (and any build that doesn't
    // override this) can never send a real motor command through Manual Drive. A future
    // real-hardware deployment flips this at the SakarC40Application call site - nothing here
    // guesses based on BuildConfig.DEBUG or any other implicit signal.
    override val manualDriveBackendMode: ManualDriveBackendMode = ManualDriveBackendMode.SIMULATED
) : AppContainer {

    private val appContext = context.applicationContext
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val db = AppDatabase.getInstance(appContext)

    // Always constructed (harmless when REAL is selected: it simply never receives a command)
    // so simulatedRobotState is always a valid, observable StateFlow for the UI to collect.
    private val simulatedMotorController = SimulatedMotorController(appScope)
    override val simulatedRobotState: StateFlow<SimulatedRobotState> = simulatedMotorController.state

    private val motionController: RobotMotionController = when (manualDriveBackendMode) {
        ManualDriveBackendMode.REAL -> RealMotorController(controller)
        ManualDriveBackendMode.SIMULATED -> simulatedMotorController.also {
            Log.i("ManualDriveSim", "SIMULATION_BACKEND_ACTIVE")
        }
    }

    override val connectionRepository: RobotConnectionRepository = RobotConnectionRepositoryImpl(controller)
    override val batteryRepository: RobotBatteryRepository = RobotBatteryRepositoryImpl(controller)
    override val chargingRepository: RobotChargingRepository = RobotChargingRepositoryImpl(controller)
    override val navigationRepository: RobotNavigationRepository =
        RobotNavigationRepositoryImpl(controller, motionController, manualDriveBackendMode)
    override val sensorsRepository: RobotSensorsRepository = RobotSensorsRepositoryImpl(controller)
    override val diagnosticsRepository: RobotDiagnosticsRepository = RobotDiagnosticsRepositoryImpl(controller)
    override val identityRepository: RobotIdentityRepository = RobotIdentityRepositoryImpl(controller, appVersionName)
    override val mapRepository: RobotMapRepository = RobotMapRepositoryImpl(controller)
    override val workstationRepository: RobotWorkstationRepository = RobotWorkstationRepositoryImpl()

    override val cleaningRepository: CleaningRepository = SimulatedCleaningRepository(appScope, db.cleaningZoneDao())
    override val scheduleRepository: ScheduleRepository = RoomScheduleRepository(db.scheduleTaskDao())
    override val consumableRepository: ConsumableRepository = RoomConsumableRepository(db.consumableDao()).also { repo ->
        appScope.launch { repo.seedIfEmpty() }
    }
    override val routeRepository: RouteRepository = RoomRouteRepository(db.routeDao())
    override val logsRepository: LogsRepository = LogsRepositoryImpl(appContext)
    override val authRepository: AuthRepository = DataStoreAuthRepository(appContext)
    override val localPreferencesRepository: LocalPreferencesRepository = DataStoreLocalPreferencesRepository(appContext)
    override val systemMaintenanceRepository: SystemMaintenanceRepository =
        SystemMaintenanceRepositoryImpl(db, authRepository, localPreferencesRepository)

    // Architecture-preparation seam (docs/architecture/SAKAR_ROBOT_SOFTWARE_ARCHITECTURE.md) - both
    // new repositories are stubs that always report NOT_AVAILABLE/NOT_VERIFIED (never a fabricated
    // value); the gateway forwards to the SAME repository instances already constructed above, not
    // new copies.
    private val mappingRepository: RobotMappingRepository = NotAvailableMappingRepository()
    private val rosRobotAdapter: RosRobotAdapter = RosRobotAdapterStub()
    private val telemetryRepository: RobotTelemetryRepository =
        RobotTelemetryRepositoryImpl(connectionRepository, batteryRepository)

    override val robotGateway: SakarRobotGateway = SakarRobotGatewayImpl(
        connection = connectionRepository,
        ros = rosRobotAdapter,
        navigation = navigationRepository,
        sensors = sensorsRepository,
        mapping = mappingRepository,
        maps = mapRepository,
        cleaning = cleaningRepository,
        charging = chargingRepository,
        diagnostics = diagnosticsRepository,
        telemetry = telemetryRepository
    )
}
