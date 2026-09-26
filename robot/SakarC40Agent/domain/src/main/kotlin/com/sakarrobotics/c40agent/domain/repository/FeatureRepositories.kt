package com.sakarrobotics.c40agent.domain.repository

import com.sakarrobotics.c40agent.domain.model.CleaningIntensity
import com.sakarrobotics.c40agent.domain.model.CleaningMode
import com.sakarrobotics.c40agent.domain.model.CleaningSession
import com.sakarrobotics.c40agent.domain.model.CleaningZone
import com.sakarrobotics.c40agent.domain.model.ConsumableItem
import com.sakarrobotics.c40agent.domain.model.LocalPreferences
import com.sakarrobotics.c40agent.domain.model.LogExportRequest
import com.sakarrobotics.c40agent.domain.model.LogExportResult
import com.sakarrobotics.c40agent.domain.model.LogRecord
import com.sakarrobotics.c40agent.domain.model.RouteRecord
import com.sakarrobotics.c40agent.domain.model.RouteRecordingState
import com.sakarrobotics.c40agent.domain.model.ScheduleTask
import kotlinx.coroutines.flow.Flow

/**
 * Cleaning execution. SIMULATED end to end on every currently-supported
 * device: the vendored Peanut SDK AAR has no cleaning-control API at all
 * (see SimulatedRobotCommandExecutor's Javadoc in :api) - this repository
 * runs a local, time-based simulation so the operator flow (start,
 * progress, pause, resume, stop, return-to-dock handoff) can be built and
 * exercised honestly, always labeled SIMULATED in the UI.
 */
interface CleaningRepository {
    val session: Flow<CleaningSession>
    suspend fun startCleaning(zoneIds: List<String>, mode: CleaningMode, intensity: CleaningIntensity, cycles: Int): Result<Unit>
    suspend fun pause(): Result<Unit>
    suspend fun resume(): Result<Unit>
    suspend fun stop(): Result<Unit>
    suspend fun listZones(mapId: String): List<CleaningZone>
    suspend fun saveZone(zone: CleaningZone)
    suspend fun deleteZone(zoneId: String)
}

/** Local (Room), designed for a future Sakar Cloud sync pass - see ScheduleTask.syncState. */
interface ScheduleRepository {
    val schedules: Flow<List<ScheduleTask>>
    suspend fun upsert(task: ScheduleTask)
    suspend fun delete(taskId: String)
    suspend fun setEnabled(taskId: String, enabled: Boolean)
}

/** Local (Room). Never backed by a wear sensor - see ConsumableItem's docs. */
interface ConsumableRepository {
    val consumables: Flow<List<ConsumableItem>>
    suspend fun recordUsageHours(id: String, additionalHours: Int)
    suspend fun resetUsage(id: String)
}

/** Local (Room) route metadata; see RouteRecord's docs for why no real path geometry is invented. */
interface RouteRepository {
    val routes: Flow<List<RouteRecord>>
    val recordingState: Flow<RouteRecordingState>
    suspend fun startRecording(name: String)
    suspend fun pauseRecording()
    suspend fun resumeRecording()
    suspend fun stopAndSaveRecording(): RouteRecord
    suspend fun rename(routeId: String, newName: String)
    suspend fun delete(routeId: String)
    /** Route playback needs a navigation path-following API the SDK does not expose - see impl. */
    suspend fun playback(routeId: String): Result<Unit>
}

interface LogsRepository {
    fun tail(limit: Int): List<LogRecord>
    val liveLog: Flow<LogRecord>
    suspend fun export(request: LogExportRequest): LogExportResult
    fun clear()
}

/** Super User gate. PIN is salted+hashed locally (DataStore) - never stored or logged in plaintext. */
interface AuthRepository {
    val role: Flow<com.sakarrobotics.c40agent.domain.model.UserRole>
    suspend fun isSuperUserPinConfigured(): Boolean
    suspend fun setSuperUserPin(pin: String)
    suspend fun verifySuperUserPin(pin: String): Boolean
    fun enterSuperUserMode()
    fun exitSuperUserMode()
    suspend fun clearSuperUserPin()
}

/** Sakar-local device preferences (general/display/sound/DND/business-profile) - real, DataStore-backed. */
interface LocalPreferencesRepository {
    val preferences: Flow<LocalPreferences>
    suspend fun update(transform: (LocalPreferences) -> LocalPreferences)
}

/**
 * Installation "Restore Factory Settings". Clears every piece of LOCAL
 * app state this app itself owns (schedules, consumables, routes, zones,
 * the Super User PIN, local preferences, the SDK call log) - it does NOT
 * and cannot reset any state stored on the robot/SDK side, since no such
 * reset API is confirmed to exist (see COMPATIBILITY_REPORT.md).
 */
interface SystemMaintenanceRepository {
    suspend fun factoryReset()
}
