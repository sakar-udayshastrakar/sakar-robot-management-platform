package com.sakarrobotics.c40agent.data.repository

import android.util.Log
import com.sakarrobotics.c40agent.diagnostics.DeviceEnvironmentInspector
import com.sakarrobotics.c40agent.domain.model.ActuatorGroup
import com.sakarrobotics.c40agent.domain.model.ActuatorTest
import com.sakarrobotics.c40agent.domain.logging.AppEventLogger
import com.sakarrobotics.c40agent.domain.logging.NoOpAppEventLogger
import com.sakarrobotics.c40agent.domain.model.ActuatorTestResult
import com.sakarrobotics.c40agent.domain.model.BatteryState
import com.sakarrobotics.c40agent.domain.model.Capability
import com.sakarrobotics.c40agent.domain.model.ChargingSettings
import com.sakarrobotics.c40agent.domain.model.CloudSyncState
import com.sakarrobotics.c40agent.domain.model.DiagnosticsSnapshot
import com.sakarrobotics.c40agent.domain.model.DockingState
import com.sakarrobotics.c40agent.domain.model.ManualDriveBackendMode
import com.sakarrobotics.c40agent.domain.model.MapListResult
import com.sakarrobotics.c40agent.domain.model.MapSummary
import com.sakarrobotics.c40agent.domain.model.Rated
import com.sakarrobotics.c40agent.domain.model.RobotIdentity
import com.sakarrobotics.c40agent.domain.model.RobotLinkState
import com.sakarrobotics.c40agent.domain.model.SensorReadings
import com.sakarrobotics.c40agent.domain.model.WorkstationState
import com.sakarrobotics.c40agent.domain.repository.RobotBatteryRepository
import com.sakarrobotics.c40agent.domain.repository.RobotChargingRepository
import com.sakarrobotics.c40agent.domain.repository.RobotConnectionRepository
import com.sakarrobotics.c40agent.domain.repository.RobotDiagnosticsRepository
import com.sakarrobotics.c40agent.domain.repository.RobotIdentityRepository
import com.sakarrobotics.c40agent.domain.repository.RobotMapRepository
import com.sakarrobotics.c40agent.domain.repository.RobotNavigationRepository
import com.sakarrobotics.c40agent.domain.repository.RobotSensorsRepository
import com.sakarrobotics.c40agent.domain.repository.RobotWorkstationRepository
import com.sakarrobotics.c40agent.robot.C40RobotController
import com.sakarrobotics.c40agent.robot.ConnectionCallback
import com.sakarrobotics.c40agent.sdk.PeanutSdkBridge
import com.sakarrobotics.c40agent.telemetry.ConnectionStatus
import kotlin.coroutines.resume
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.suspendCancellableCoroutine

private fun ConnectionStatus.toDomain(): RobotLinkState = when (this) {
    ConnectionStatus.DISCONNECTED -> RobotLinkState.DISCONNECTED
    ConnectionStatus.CONNECTING -> RobotLinkState.CONNECTING
    ConnectionStatus.CONNECTED -> RobotLinkState.CONNECTED
    ConnectionStatus.INIT_FAILED -> RobotLinkState.INIT_FAILED
}

class RobotConnectionRepositoryImpl(
    private val controller: C40RobotController,
    private val tracker: CommunicationTracker = CommunicationTracker(),
    private val events: AppEventLogger = NoOpAppEventLogger,
    private val pollIntervalMillis: Long = LINK_POLL_INTERVAL_MILLIS
) : RobotConnectionRepository {

    // C40RobotController has no listener API for connection-status changes, but exposes its state
    // through getStatus() - a plain in-memory field read, nothing is sent to the robot. While anyone
    // is collecting, this flow re-reads it periodically so the UI cannot keep showing a stale value
    // after the controller's status changes outside connect()/disconnect().
    override val linkState: Flow<RobotLinkState> = flow {
        while (true) {
            emit(controller.status.toDomain())
            delay(pollIntervalMillis)
        }
    }.distinctUntilChanged()

    override val lastCommunicationMillis: Flow<Long?> = tracker.lastSuccessMillis

    // No Sakar Cloud MQTT client is wired into :operator-ui (Phase 3/6/7 MQTT lives in :app /
    // :api and is not exposed through this container) - reported honestly as OFFLINE rather than
    // faking a cloud link the UI has no way to actually observe.
    private val _cloudSyncState = MutableStateFlow(CloudSyncState.OFFLINE)
    override val cloudSyncState: Flow<CloudSyncState> = _cloudSyncState.asStateFlow()

    override suspend fun connect(): Result<Unit> = suspendCancellableCoroutine { cont ->
        events.info("Connection", "Connect requested")
        controller.connect(object : ConnectionCallback {
            override fun onConnected() {
                tracker.markSuccess()
                events.info("Connection", "SDK initialised; link CONNECTED")
                if (cont.isActive) cont.resume(Result.success(Unit))
            }

            override fun onConnectionFailed(errorCode: Int) {
                events.error("Connection", "SDK initialisation failed (error $errorCode)")
                if (cont.isActive) cont.resume(Result.failure(SdkCallException(errorCode, "SDK init failed")))
            }
        })
    }

    override fun disconnect() {
        controller.disconnect()
        events.info("Connection", "Disconnected by operator")
    }

    private companion object {
        const val LINK_POLL_INTERVAL_MILLIS = 1_000L
    }
}

class RobotBatteryRepositoryImpl(
    private val controller: C40RobotController,
    private val tracker: CommunicationTracker = CommunicationTracker(),
    private val events: AppEventLogger = NoOpAppEventLogger
) : RobotBatteryRepository {

    private val _battery = MutableStateFlow(initialBatteryState())
    override val battery: Flow<BatteryState> = _battery.asStateFlow()

    override suspend fun refresh(): BatteryState {
        val result = tracker.call { controller.getBattery(it) }
        // The vendored AAR's BatteryComponent.getStatus() response shape is not confirmed
        // (COMPATIBILITY_REPORT.md) - percentage/charging/temperature are deliberately left null
        // (capability UNKNOWN) rather than guess-parsed; a failed query is UNAVAILABLE. The raw
        // response is still shown in full, verbatim, in the UI.
        result.exceptionOrNull()?.let { events.warn("Battery", "Battery query failed: ${it.message}") }
        val state = result.toBatteryState()
        _battery.value = state
        return state
    }
}

class RobotChargingRepositoryImpl(private val controller: C40RobotController) : RobotChargingRepository {

    private val _dockingState = MutableStateFlow(DockingState(isDocked = false, isReturning = false, capability = Capability.GATED))
    override val dockingState: Flow<DockingState> = _dockingState.asStateFlow()

    // No charging-threshold configuration API exists on the vendored SDK - these are Sakar-local
    // operator preferences only, not read from or pushed to the robot.
    override var settings: ChargingSettings = ChargingSettings(
        autoRechargeEnabled = true,
        autoRechargeLevelPercent = 50,
        idleChargingIntervalMinutes = 15,
        taskBatteryProtectionEnabled = true,
        minimumOperatingBatteryPercent = 10,
        taskChargingIntervalMinutes = 5
    )

    override suspend fun returnToDock(): Result<Unit> {
        _dockingState.value = _dockingState.value.copy(isReturning = true)
        val result = awaitSdkCall { controller.returnToDock(it) }.map { }
        _dockingState.value = _dockingState.value.copy(isReturning = false)
        return result
    }

    override suspend fun startCharging(): Result<Unit> = awaitSdkCall { controller.startCharging(it) }.map { }

    override suspend fun stopCharging(): Result<Unit> = awaitSdkCall { controller.stopCharging(it) }.map { }
}

class RobotNavigationRepositoryImpl(
    private val controller: C40RobotController,
    private val motionController: RobotMotionController,
    private val backendMode: ManualDriveBackendMode
) : RobotNavigationRepository {

    override suspend fun goToDestination(destinationId: Int): Result<Unit> =
        awaitSdkCall { controller.goToPoint(destinationId, it) }.map { }

    override suspend fun pause(): Result<Unit> = awaitSdkCall { controller.pauseNavigation(it) }.map { }

    override suspend fun resume(): Result<Unit> = awaitSdkCall { controller.resumeNavigation(it) }.map { }

    override suspend fun stop(): Result<Unit> = awaitSdkCall { controller.stopNavigation(it) }.map { }

    // Routed through RobotMotionController rather than calling C40RobotController directly, so the
    // exact same UI/ViewModel/use-case chain can drive either the REAL MotorComponent backend or
    // the SIMULATED virtual robot depending on which one DefaultAppContainer selected - see
    // RobotMotionController.kt. angular takes priority over linear, matching ManualDriveUseCases'
    // own LEFT/RIGHT-vs-FORWARD/REVERSE precedence (a jog command is never both a turn and a
    // straight-line move at once).
    override suspend fun jog(linearVelocity: Float, angularVelocity: Float): Result<Unit> {
        val direction = when {
            angularVelocity > 0f -> "LEFT"
            angularVelocity < 0f -> "RIGHT"
            linearVelocity > 0f -> "FORWARD"
            linearVelocity < 0f -> "REVERSE"
            else -> "STOP"
        }
        val result = when (direction) {
            "LEFT" -> motionController.turnLeft("operator")
            "RIGHT" -> motionController.turnRight("operator")
            "FORWARD" -> motionController.forward("operator")
            "REVERSE" -> motionController.backward("operator")
            else -> motionController.stop("operator")
        }
        Log.i(
            "ManualDrive",
            "MANUAL_DRIVE_COMMAND direction=$direction source=operator hardware=${backendMode.name} " +
                "result=${if (result.isSuccess) "SUCCESS" else "FAILURE"} timestamp=${System.currentTimeMillis()}"
        )
        return result
    }
}

class RobotSensorsRepositoryImpl(
    private val controller: C40RobotController,
    private val tracker: CommunicationTracker = CommunicationTracker(),
    private val events: AppEventLogger = NoOpAppEventLogger
) : RobotSensorsRepository {

    override suspend fun readSensors(): SensorReadings = SensorReadings(
        lidar = tracker.call { controller.getLidar(it) }.rated("LiDAR"),
        depth = tracker.call { controller.getDepth(it) }.rated("Depth camera"),
        sonar = tracker.call { controller.getSonar(it) }.rated("Sonar"),
        imu = tracker.call { controller.getImu(it) }.rated("IMU")
    )

    override suspend fun readPosition(): Rated<String> =
        // UNCONFIRMED on the physical C40 - see C40RobotController.getPosition's Javadoc.
        tracker.call { controller.getPosition(it) }.rated("Position")

    private fun Result<String>.rated(label: String): Rated<String> {
        exceptionOrNull()?.let { events.warn("Sensors", "$label read failed: ${it.message}") }
        return toRatedReading()
    }
}

class RobotDiagnosticsRepositoryImpl(
    private val controller: C40RobotController,
    private val tracker: CommunicationTracker = CommunicationTracker(),
    private val events: AppEventLogger = NoOpAppEventLogger
) : RobotDiagnosticsRepository {

    override suspend fun snapshot(): DiagnosticsSnapshot {
        val info = controller.runtimeInfo
        val health = controller.health
        val heartbeat = controller.heartbeat
        // RuntimeInfo yields zero defaults when the SDK link is not up - those are not readings.
        val linked = controller.status == ConnectionStatus.CONNECTED
        return DiagnosticsSnapshot(
            capability = if (linked) Capability.UNKNOWN else Capability.UNAVAILABLE,
            workMode = info.workMode,
            syncStatus = info.syncStatus,
            power = info.power,
            totalOdometer = info.totalOdo,
            emergencyStopEngaged = info.isEmergencyOpen,
            emergencyEnabled = info.isEmergencyEnable,
            motorStatusCode = info.motorStatus,
            robotArmInfo = info.robotArmInfo,
            robotStm32Info = info.robotStm32Info,
            lastHealthEvent = health?.rawContent,
            lastHeartbeat = heartbeat?.rawContent
        )
    }

    // Actuator groups/tests mirror the Super User "Robot Debugging" screens studied from the
    // reference on-robot app. Every test id maps 1:1 to a real C40RobotController/PeanutSdkBridge
    // call where one exists; the vendored SDK has no per-component actuator test API (spin brush
    // N, run fan at X%, jog a wheel, ...) at all, so every ActuatorTest below is UNAVAILABLE and
    // runActuatorTest() never pretends to have actuated anything - it only reports that fact.
    override fun actuatorGroups(): List<ActuatorGroup> = listOf(
        ActuatorGroup("power_motion", "Power & Motion", "Battery, IMU, motors, wheels"),
        ActuatorGroup("cleaning_system", "Cleaning System", "Brushes, fans, pumps, water system"),
        ActuatorGroup("safety_sensors", "Safety & Sensors", "Bumpers, emergency stop, LEDs"),
        ActuatorGroup("docking_comms", "Docking & Communication", "Charging, docking, navigation link")
    )

    override fun actuatorTests(groupId: String): List<ActuatorTest> = when (groupId) {
        "power_motion" -> listOf(
            ActuatorTest("battery_read", groupId, "Read battery status", false, Capability.REAL),
            ActuatorTest("motor_status_read", groupId, "Read motor status/health", false, Capability.REAL),
            ActuatorTest("motor_encoder_read", groupId, "Read motor encoder", false, Capability.REAL),
            ActuatorTest("motor_speed_read", groupId, "Read motor speed", false, Capability.REAL),
            ActuatorTest("motor_state_read", groupId, "Read motor state", false, Capability.REAL),
            ActuatorTest("imu_read", groupId, "Read IMU", false, Capability.REAL),
            ActuatorTest("wheel_jog_test", groupId, "Jog wheels (test)", true, Capability.UNAVAILABLE)
        )
        "cleaning_system" -> listOf(
            ActuatorTest("side_brush_test", groupId, "Side brush spin test", true, Capability.UNAVAILABLE),
            ActuatorTest("fan_test", groupId, "Vacuum fan test", true, Capability.UNAVAILABLE),
            ActuatorTest("pump_test", groupId, "Water pump test", true, Capability.UNAVAILABLE)
        )
        "safety_sensors" -> listOf(
            ActuatorTest("lidar_read", groupId, "Read LiDAR", false, Capability.REAL),
            ActuatorTest("depth_read", groupId, "Read depth camera", false, Capability.REAL),
            ActuatorTest("sonar_read", groupId, "Read sonar", false, Capability.REAL),
            ActuatorTest("bumper_test", groupId, "Bumper contact test", true, Capability.UNAVAILABLE),
            ActuatorTest("led_test", groupId, "Light strip test", true, Capability.UNAVAILABLE)
        )
        "docking_comms" -> listOf(
            // NavigationComponent.getStatus lives on NavigationBridge and is not exposed through the controller.
            ActuatorTest("nav_status_read", groupId, "Read navigation status", false, Capability.UNAVAILABLE),
            ActuatorTest("map_info_read", groupId, "Read map info", false, Capability.REAL),
            ActuatorTest("return_to_dock_test", groupId, "Return-to-dock (gated)", true, Capability.GATED)
        )
        else -> emptyList()
    }

    override suspend fun runActuatorTest(testId: String): ActuatorTestResult {
        val now = System.currentTimeMillis()
        val result: Result<String> = when (testId) {
            "battery_read" -> tracker.call { controller.getBattery(it) }
            "motor_status_read" -> tracker.call { controller.getMotorStatus(it) }
            "motor_encoder_read" -> tracker.call { controller.getMotorEncoder(it) }
            "motor_speed_read" -> tracker.call { controller.getMotorSpeed(it) }
            "motor_state_read" -> tracker.call { controller.getMotorState(it) }
            "imu_read" -> tracker.call { controller.getImu(it) }
            "lidar_read" -> tracker.call { controller.getLidar(it) }
            "depth_read" -> tracker.call { controller.getDepth(it) }
            "sonar_read" -> tracker.call { controller.getSonar(it) }
            "nav_status_read" -> Result.failure(UnsupportedOperationException(
                "Navigation status is not exposed by the controller - no reading was taken."))
            "map_info_read" -> tracker.call { controller.getMapInfo(it) }
            "return_to_dock_test" -> awaitSdkCall { controller.returnToDock(it) }
            else -> Result.failure(UnsupportedOperationException(
                "No actuator API exists in the vendored Peanut SDK for this test."))
        }
        result.exceptionOrNull()?.let { events.warn("Diagnostics", "Test '$testId' did not succeed: ${it.message}") }
        return result.toActuatorTestResult(testId, now)
    }
}

class RobotIdentityRepositoryImpl(
    private val controller: C40RobotController,
    private val appVersionName: String
) : RobotIdentityRepository {

    override suspend fun identity(): RobotIdentity {
        val runtime = controller.runtimeInfo
        return RobotIdentity(
            robotName = "Sakar CleanBot",
            productLine = "Sakar Robotics",
            model = "Sakar CleanBot 5000 Plus",
            // Not exposed by any confirmed SDK API (see COMPATIBILITY_REPORT.md) - left null
            // rather than substituting the Android tablet's own (privacy-restricted, unreliable)
            // identifiers as if they were the robot's.
            serialNumber = null,
            macAddress = null,
            firmwareVersion = null,
            androidVersion = DeviceEnvironmentInspector.getAndroidVersion(),
            appVersionName = appVersionName,
            sdkVersion = PeanutSdkBridge.SDK_VERSION,
            robotIp = runtime.robotIp
        )
    }
}

class RobotMapRepositoryImpl(
    private val controller: C40RobotController,
    private val tracker: CommunicationTracker = CommunicationTracker(),
    private val events: AppEventLogger = NoOpAppEventLogger
) : RobotMapRepository {

    override suspend fun listMaps(): List<MapSummary> = listMapsDetailed().maps

    override suspend fun listMapsDetailed(): MapListResult {
        // C40RobotController.getMapInfo has no "SDK ready" guard of its own, so check the link here
        // instead of calling into an uninitialised SDK.
        if (controller.status != ConnectionStatus.CONNECTED) {
            return MapListResult(emptyList(), "Not connected - the robot SDK is not initialised.")
        }
        val result = tracker.call { controller.getMapInfo(it) }.toMapListResult()
        result.error?.let { events.warn("Maps", "Map info read failed: $it") }
        return result
    }

    override suspend fun mapImageBytes(mapId: String): Rated<ByteArray?> {
        // MapComponent.downloadOpt returns a raw CoAP response via the SdkCallback string
        // channel, not proven to be renderable image bytes (see PeanutSdkBridge's own
        // "VENDOR CLASS-NAME TRAP" note) - reported as REAL data available, but NOT decoded as an
        // image without confirmed format, to avoid rendering garbage as if it were a real floor
        // plan.
        return Rated(null, Capability.UNAVAILABLE)
    }

    override suspend fun deployMap(mapId: String): Result<Unit> =
        Result.failure(UnsupportedOperationException(
            "Map deployment requires MapComponent.uploadOpt with a confirmed map file format - not yet validated."))
}

class RobotWorkstationRepositoryImpl : RobotWorkstationRepository {
    // No smart-workstation (auto water refill/drainage/self-dock charging base) API exists
    // anywhere in the vendored Peanut SDK - this is a real interface with no backing hardware
    // integration yet, exactly per the "build the abstraction, mark unavailable" instruction.
    private val _state = MutableStateFlow(
        WorkstationState(
            connected = false,
            waterRefillEnabled = false,
            drainageEnabled = false,
            detergentEnabled = false,
            waterToDetergentRatio = 60,
            wasteWaterLevelPercent = null,
            capability = Capability.UNAVAILABLE
        )
    )
    override val state: Flow<WorkstationState> = _state.asStateFlow()
    override suspend fun refresh(): WorkstationState = _state.value
}
