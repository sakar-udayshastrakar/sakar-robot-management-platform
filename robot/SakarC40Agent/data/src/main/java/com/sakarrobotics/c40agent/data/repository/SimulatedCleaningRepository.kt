package com.sakarrobotics.c40agent.data.repository

import com.sakarrobotics.c40agent.data.db.CleaningZoneDao
import com.sakarrobotics.c40agent.data.db.CleaningZoneEntity
import com.sakarrobotics.c40agent.domain.model.Capability
import com.sakarrobotics.c40agent.domain.model.CleaningIntensity
import com.sakarrobotics.c40agent.domain.model.CleaningMode
import com.sakarrobotics.c40agent.domain.model.CleaningRunState
import com.sakarrobotics.c40agent.domain.model.CleaningSession
import com.sakarrobotics.c40agent.domain.model.CleaningZone
import com.sakarrobotics.c40agent.domain.model.PointXY
import com.sakarrobotics.c40agent.domain.repository.CleaningRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TICK_MS = 1000L
private const val SIMULATED_SECONDS_PER_PERCENT = 3L

/**
 * The vendored Peanut SDK AAR has NO cleaning-control API at all (verified
 * across COMPATIBILITY_REPORT.md and SimulatedRobotCommandExecutor's own
 * Javadoc in :api - the same conclusion this project already reached for
 * the MQTT START_TASK command). This repository is a local, time-based
 * simulation of a cleaning run so the operator UI (progress, pause,
 * resume, stop, cycles) can be built and demonstrated honestly. Every
 * value it emits carries Capability.SIMULATED - the UI must render that
 * badge, never hide it.
 */
class SimulatedCleaningRepository(
    private val scope: CoroutineScope,
    private val zoneDao: CleaningZoneDao
) : CleaningRepository {

    private val _session = MutableStateFlow(idleSession())
    override val session: Flow<CleaningSession> = _session.asStateFlow()

    private var tickJob: Job? = null

    override suspend fun startCleaning(
        zoneIds: List<String>,
        mode: CleaningMode,
        intensity: CleaningIntensity,
        cycles: Int
    ): Result<Unit> {
        tickJob?.cancel()
        _session.value = CleaningSession(
            state = CleaningRunState.CLEANING,
            zoneIds = zoneIds,
            mode = mode,
            intensity = intensity,
            progressPercent = 0,
            elapsedSeconds = 0,
            estimatedRemainingSeconds = 100L * SIMULATED_SECONDS_PER_PERCENT,
            cyclesRequested = cycles,
            cyclesCompleted = 0,
            capability = Capability.SIMULATED
        )
        tickJob = scope.launch { runSimulation() }
        return Result.success(Unit)
    }

    private suspend fun runSimulation() {
        while (true) {
            delay(TICK_MS)
            val current = _session.value
            if (current.state != CleaningRunState.CLEANING) continue
            val nextElapsed = current.elapsedSeconds + 1
            val nextProgress = ((nextElapsed / SIMULATED_SECONDS_PER_PERCENT).toInt()).coerceAtMost(100)
            if (nextProgress >= 100) {
                val cyclesDone = current.cyclesCompleted + 1
                if (cyclesDone >= current.cyclesRequested) {
                    _session.value = current.copy(
                        state = CleaningRunState.COMPLETED,
                        progressPercent = 100,
                        elapsedSeconds = nextElapsed,
                        estimatedRemainingSeconds = 0,
                        cyclesCompleted = cyclesDone
                    )
                    return
                }
                _session.value = current.copy(
                    progressPercent = 0,
                    elapsedSeconds = nextElapsed,
                    cyclesCompleted = cyclesDone,
                    estimatedRemainingSeconds = 100L * SIMULATED_SECONDS_PER_PERCENT
                )
            } else {
                _session.value = current.copy(
                    progressPercent = nextProgress,
                    elapsedSeconds = nextElapsed,
                    estimatedRemainingSeconds = (100 - nextProgress) * SIMULATED_SECONDS_PER_PERCENT
                )
            }
        }
    }

    override suspend fun pause(): Result<Unit> {
        if (_session.value.state != CleaningRunState.CLEANING) return Result.success(Unit)
        _session.value = _session.value.copy(state = CleaningRunState.PAUSED)
        return Result.success(Unit)
    }

    override suspend fun resume(): Result<Unit> {
        if (_session.value.state != CleaningRunState.PAUSED) return Result.success(Unit)
        _session.value = _session.value.copy(state = CleaningRunState.CLEANING)
        return Result.success(Unit)
    }

    override suspend fun stop(): Result<Unit> {
        tickJob?.cancel()
        _session.value = idleSession()
        return Result.success(Unit)
    }

    override suspend fun listZones(mapId: String): List<CleaningZone> =
        zoneDao.forMap(mapId).map { it.toDomain() }

    override suspend fun saveZone(zone: CleaningZone) = zoneDao.upsert(zone.toEntity())

    override suspend fun deleteZone(zoneId: String) = zoneDao.delete(zoneId)

    private fun idleSession() = CleaningSession(
        state = CleaningRunState.IDLE,
        zoneIds = emptyList(),
        mode = CleaningMode.SWEEP,
        intensity = CleaningIntensity.STANDARD,
        progressPercent = 0,
        elapsedSeconds = 0,
        estimatedRemainingSeconds = 0,
        cyclesRequested = 1,
        cyclesCompleted = 0,
        capability = Capability.SIMULATED
    )
}

private fun CleaningZoneEntity.toDomain() = CleaningZone(
    id = id,
    mapId = mapId,
    name = name,
    polygon = polygonCsv.split(";").filter { it.isNotBlank() }.map {
        val (x, y) = it.split(",")
        PointXY(x.toFloat(), y.toFloat())
    },
    floor = floor
)

private fun CleaningZone.toEntity() = CleaningZoneEntity(
    id = id,
    mapId = mapId,
    name = name,
    polygonCsv = polygon.joinToString(";") { "${it.x},${it.y}" },
    floor = floor
)
