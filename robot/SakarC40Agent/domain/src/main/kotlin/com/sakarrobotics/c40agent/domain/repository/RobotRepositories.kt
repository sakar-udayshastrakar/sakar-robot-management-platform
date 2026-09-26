package com.sakarrobotics.c40agent.domain.repository

import com.sakarrobotics.c40agent.domain.model.ActuatorGroup
import com.sakarrobotics.c40agent.domain.model.ActuatorTest
import com.sakarrobotics.c40agent.domain.model.ActuatorTestResult
import com.sakarrobotics.c40agent.domain.model.BatteryState
import com.sakarrobotics.c40agent.domain.model.ChargingSettings
import com.sakarrobotics.c40agent.domain.model.CloudSyncState
import com.sakarrobotics.c40agent.domain.model.DiagnosticsSnapshot
import com.sakarrobotics.c40agent.domain.model.DockingState
import com.sakarrobotics.c40agent.domain.model.MapSummary
import com.sakarrobotics.c40agent.domain.model.Rated
import com.sakarrobotics.c40agent.domain.model.RobotIdentity
import com.sakarrobotics.c40agent.domain.model.RobotLinkState
import com.sakarrobotics.c40agent.domain.model.SensorReadings
import com.sakarrobotics.c40agent.domain.model.WorkstationState
import kotlinx.coroutines.flow.Flow

interface RobotConnectionRepository {
    val linkState: Flow<RobotLinkState>
    val cloudSyncState: Flow<CloudSyncState>
    suspend fun connect(): Result<Unit>
    fun disconnect()
}

interface RobotBatteryRepository {
    val battery: Flow<BatteryState>
    suspend fun refresh(): BatteryState
}

interface RobotChargingRepository {
    val dockingState: Flow<DockingState>
    var settings: ChargingSettings
    /** GATED by OperatingMode.HARDWARE_TEST - see C40RobotController. Returns false (not thrown) when blocked. */
    suspend fun returnToDock(): Result<Unit>
    suspend fun startCharging(): Result<Unit>
    suspend fun stopCharging(): Result<Unit>
}

interface RobotNavigationRepository {
    suspend fun goToDestination(destinationId: Int): Result<Unit>
    suspend fun pause(): Result<Unit>
    suspend fun resume(): Result<Unit>
    suspend fun stop(): Result<Unit>
    /** Manual-drive jog command. GATED, and always STOP on release - see ManualDriveUseCases. */
    suspend fun jog(linearVelocity: Float, angularVelocity: Float): Result<Unit>
}

interface RobotSensorsRepository {
    suspend fun readSensors(): SensorReadings
    suspend fun readPosition(): Rated<String>
}

interface RobotDiagnosticsRepository {
    suspend fun snapshot(): DiagnosticsSnapshot
    fun actuatorGroups(): List<ActuatorGroup>
    fun actuatorTests(groupId: String): List<ActuatorTest>
    suspend fun runActuatorTest(testId: String): ActuatorTestResult
}

interface RobotIdentityRepository {
    suspend fun identity(): RobotIdentity
}

interface RobotMapRepository {
    suspend fun listMaps(): List<MapSummary>
    suspend fun mapImageBytes(mapId: String): Rated<ByteArray?>
    /** Pushing a new/edited map to the robot. GATED and, on current hardware, UNAVAILABLE - see impl. */
    suspend fun deployMap(mapId: String): Result<Unit>
}

interface RobotWorkstationRepository {
    val state: Flow<WorkstationState>
    suspend fun refresh(): WorkstationState
}
