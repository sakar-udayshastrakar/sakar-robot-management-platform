package com.sakarrobotics.c40agent.domain.model

/** Mirrors [com.sakarrobotics.c40agent.telemetry.ConnectionStatus] without depending on it. */
enum class RobotLinkState { DISCONNECTED, CONNECTING, CONNECTED, INIT_FAILED }

/** Sakar Cloud reachability - independent of the on-robot SDK link above. */
enum class CloudSyncState { ONLINE, OFFLINE, RECONNECTING, SYNCING }

enum class UserRole { OPERATOR, SUPER_USER }

data class BatteryState(
    val percentage: Int?,
    val isCharging: Boolean,
    val temperatureCelsius: Double?,
    val raw: String?,
    val capability: Capability
)

data class ChargingSettings(
    val autoRechargeEnabled: Boolean,
    val autoRechargeLevelPercent: Int,
    val idleChargingIntervalMinutes: Int,
    val taskBatteryProtectionEnabled: Boolean,
    val minimumOperatingBatteryPercent: Int,
    val taskChargingIntervalMinutes: Int
)

data class DockingState(
    val isDocked: Boolean,
    val isReturning: Boolean,
    val capability: Capability
)

data class RobotIdentity(
    val robotName: String,
    val productLine: String,
    val model: String,
    val serialNumber: String?,
    val macAddress: String?,
    val firmwareVersion: String?,
    val androidVersion: String,
    val appVersionName: String,
    val sdkVersion: String,
    val robotIp: String?
)

data class SensorReadings(
    val lidar: Rated<String>,
    val depth: Rated<String>,
    val sonar: Rated<String>,
    val imu: Rated<String>
)

data class DiagnosticsSnapshot(
    val workMode: Int,
    val syncStatus: Int,
    val power: Int,
    val totalOdometer: Double?,
    val emergencyStopEngaged: Boolean,
    val emergencyEnabled: Boolean,
    val motorStatusCode: Int,
    val robotArmInfo: String?,
    val robotStm32Info: String?,
    val lastHealthEvent: String?,
    val lastHeartbeat: String?
)

data class ActuatorGroup(
    val id: String,
    val label: String,
    val description: String
)

data class ActuatorTest(
    val id: String,
    val groupId: String,
    val label: String,
    val requiresConfirmation: Boolean,
    val capability: Capability
)

data class ActuatorTestResult(
    val testId: String,
    val success: Boolean,
    val message: String,
    val timestampMillis: Long
)

data class MapSummary(
    val id: String,
    val name: String,
    val inUse: Boolean,
    val sizeBytes: Long?,
    val capability: Capability
)

data class WorkstationState(
    val connected: Boolean,
    val waterRefillEnabled: Boolean,
    val drainageEnabled: Boolean,
    val detergentEnabled: Boolean,
    val waterToDetergentRatio: Int,
    val wasteWaterLevelPercent: Int?,
    val capability: Capability
)
