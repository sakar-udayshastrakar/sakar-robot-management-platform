package com.sakarrobotics.installation.core.data

import com.sakarrobotics.installation.core.model.BackendInfo
import com.sakarrobotics.installation.core.model.BackendMode
import com.sakarrobotics.installation.core.model.ErrorCode
import com.sakarrobotics.installation.core.model.MapStatus
import com.sakarrobotics.installation.core.model.MappingPhase
import com.sakarrobotics.installation.core.model.MappingSessionState
import com.sakarrobotics.installation.core.model.Outcome
import com.sakarrobotics.installation.core.model.SimMap
import com.sakarrobotics.installation.core.repository.FloorRepository
import com.sakarrobotics.installation.core.repository.MappingRepository
import com.sakarrobotics.installation.core.sim.MappingSimulation
import com.sakarrobotics.installation.core.sim.SimulatedFloorPlan
import com.sakarrobotics.installation.core.storage.Clock
import com.sakarrobotics.installation.core.storage.IdGenerator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

object SimulatedBackend {
    val info = BackendInfo(
        mode = BackendMode.SIMULATED,
        label = "SIMULATED BACKEND",
        description = "All robot-dependent data in this app is generated locally. No robot, sensor, " +
            "map service or network is contacted."
    )
}

/**
 * SIMULATED mapping repository: a local state machine that reveals a fake floor plan over time.
 * It never contacts a robot and never produces a real map. An interrupted session is not restored
 * after the app restarts (only saved maps persist).
 */
class SimulatedMappingRepository(
    private val mapStore: SimMapStore,
    private val floors: FloorRepository,
    private val clock: Clock,
    private val ids: IdGenerator,
    private val scope: CoroutineScope,
    private val tickMillis: Long = DEFAULT_TICK_MILLIS,
    private val cellsPerStep: Int = DEFAULT_CELLS_PER_STEP
) : MappingRepository {

    override val backend: BackendInfo = SimulatedBackend.info

    private val lock = Any()
    private var simulation: MappingSimulation? = null
    private var ticker: Job? = null

    private val _session = MutableStateFlow(MappingSessionState())
    override val session: StateFlow<MappingSessionState> = _session.asStateFlow()

    override val maps: StateFlow<List<SimMap>> = mapStore.maps

    private val _loaded = MutableStateFlow<SimMap?>(null)
    override val loadedMap: StateFlow<SimMap?> = _loaded.asStateFlow()

    // ------------------------------------------------------------------ session

    override fun startSession(floorId: String): Outcome<Unit> = synchronized(lock) {
        if (_session.value.isActive) {
            return Outcome.Failure(ErrorCode.WRONG_STATE, "A simulated mapping session is already running.")
        }
        if (floors.floors.value.none { it.id == floorId }) {
            return Outcome.Failure(ErrorCode.NOT_FOUND, "Select a floor before starting a simulated session.")
        }
        val sim = MappingSimulation(SimulatedFloorPlan.generate(floorId), cellsPerStep)
        simulation = sim
        _loaded.value = null
        _session.value = MappingSessionState(
            phase = MappingPhase.MAPPING,
            floorId = floorId,
            progressPercent = 0,
            grid = sim.snapshot(),
            robotX = sim.robotX,
            robotY = sim.robotY,
            message = "Simulated mapping started. No robot is mapping."
        )
        launchTicker()
        Outcome.Success(Unit)
    }

    override fun pause(): Outcome<Unit> = synchronized(lock) {
        if (_session.value.phase != MappingPhase.MAPPING) return wrongState("Only a running session can be paused.")
        stopTicker()
        _session.value = _session.value.copy(phase = MappingPhase.PAUSED, message = "Simulated session paused.")
        Outcome.Success(Unit)
    }

    override fun resume(): Outcome<Unit> = synchronized(lock) {
        if (_session.value.phase != MappingPhase.PAUSED) return wrongState("Only a paused session can be resumed.")
        _session.value = _session.value.copy(phase = MappingPhase.MAPPING, message = "Simulated session resumed.")
        launchTicker()
        Outcome.Success(Unit)
    }

    override fun finish(): Outcome<Unit> = synchronized(lock) {
        val state = _session.value
        if (!state.isActive) return wrongState("There is no running simulated session to finish.")
        if (state.progressPercent < MIN_FINISH_PERCENT) {
            return Outcome.Failure(
                ErrorCode.INSUFFICIENT_COVERAGE,
                "Scan at least $MIN_FINISH_PERCENT% before finishing (currently ${state.progressPercent}%)."
            )
        }
        stopTicker()
        _session.value = state.copy(
            phase = MappingPhase.FINISHED,
            message = "Simulated scan finished. Name the map and save it."
        )
        Outcome.Success(Unit)
    }

    override fun cancel(): Outcome<Unit> = synchronized(lock) {
        val phase = _session.value.phase
        if (phase == MappingPhase.IDLE || phase == MappingPhase.CANCELLED) {
            return wrongState("There is no simulated session to cancel.")
        }
        stopTicker()
        simulation = null
        _session.value = MappingSessionState(
            phase = MappingPhase.CANCELLED,
            message = "Simulated session cancelled. Nothing was saved."
        )
        Outcome.Success(Unit)
    }

    override fun interrupt(reason: String) = synchronized(lock) {
        if (_session.value.phase == MappingPhase.MAPPING) {
            stopTicker()
            _session.value = _session.value.copy(phase = MappingPhase.PAUSED, message = reason)
        }
    }

    override fun shutdown() = synchronized(lock) {
        stopTicker()
    }

    override fun resetSession() = synchronized(lock) {
        stopTicker()
        simulation = null
        _session.value = MappingSessionState()
    }

    /** Advances the simulation by [steps] ticks. The timer calls this; tests call it directly. */
    fun advance(steps: Int = 1) = synchronized(lock) {
        repeat(steps) {
            val sim = simulation
            val state = _session.value
            if (sim == null || state.phase != MappingPhase.MAPPING) return@synchronized
            val more = sim.step()
            _session.value = state.copy(
                progressPercent = sim.progressPercent,
                grid = sim.snapshot(),
                robotX = sim.robotX,
                robotY = sim.robotY
            )
            if (!more) {
                stopTicker()
                _session.value = _session.value.copy(
                    phase = MappingPhase.FINISHED,
                    message = "Simulated scan covered the whole floor plan. Name the map and save it."
                )
            }
        }
    }

    private fun launchTicker() {
        stopTicker()
        if (tickMillis <= 0) return // manual stepping (tests)
        ticker = scope.launch {
            while (isActive && _session.value.phase == MappingPhase.MAPPING) {
                delay(tickMillis)
                advance(1)
            }
        }
    }

    private fun stopTicker() {
        ticker?.cancel()
        ticker = null
    }

    // --------------------------------------------------------------- map library

    override fun saveMap(name: String): Outcome<SimMap> = synchronized(lock) {
        val state = _session.value
        if (state.phase != MappingPhase.FINISHED || state.grid == null || state.floorId == null) {
            return wrongState("Finish a simulated scan before saving a map.")
        }
        val floorId = state.floorId
        if (floors.floors.value.none { it.id == floorId }) {
            return Outcome.Failure(ErrorCode.NOT_FOUND, "The floor for this session no longer exists.")
        }
        val valid = NameRules.validate(name, "map")
        if (valid is Outcome.Failure) return valid
        val clean = (valid as Outcome.Success).value
        if (mapStore.maps.value.size >= NameRules.MAX_MAPS) {
            return Outcome.Failure(ErrorCode.LIMIT_REACHED, "At most ${NameRules.MAX_MAPS} maps can be saved. Delete one first.")
        }
        if (isDuplicate(floorId, clean, exceptId = null)) {
            return Outcome.Failure(ErrorCode.DUPLICATE_NAME, "A map named \"$clean\" already exists on this floor.")
        }
        val map = SimMap(
            id = ids.next("map"),
            name = clean,
            floorId = floorId,
            createdAtMillis = clock.nowMillis(),
            status = if (state.progressPercent >= COMPLETE_PERCENT) MapStatus.COMPLETE else MapStatus.PARTIAL,
            coveragePercent = state.progressPercent,
            preview = state.grid
        )
        mapStore.add(map)
        simulation = null
        _session.value = MappingSessionState(message = "Saved simulated map \"$clean\".")
        _loaded.value = map
        Outcome.Success(map)
    }

    override fun loadMap(id: String): Outcome<SimMap> = synchronized(lock) {
        if (_session.value.isActive) {
            return wrongState("Finish or cancel the current simulated session before opening a saved map.")
        }
        val map = mapStore.find(id) ?: return Outcome.Failure(ErrorCode.NOT_FOUND, "That map no longer exists.")
        _loaded.value = map
        Outcome.Success(map)
    }

    override fun closeLoadedMap() {
        _loaded.value = null
    }

    override fun renameMap(id: String, name: String): Outcome<SimMap> = synchronized(lock) {
        val existing = mapStore.find(id) ?: return Outcome.Failure(ErrorCode.NOT_FOUND, "That map no longer exists.")
        val valid = NameRules.validate(name, "map")
        if (valid is Outcome.Failure) return valid
        val clean = (valid as Outcome.Success).value
        if (isDuplicate(existing.floorId, clean, exceptId = id)) {
            return Outcome.Failure(ErrorCode.DUPLICATE_NAME, "A map named \"$clean\" already exists on this floor.")
        }
        val updated = existing.copy(name = clean)
        mapStore.replace(updated)
        if (_loaded.value?.id == id) _loaded.value = updated
        Outcome.Success(updated)
    }

    override fun deleteMap(id: String): Outcome<Unit> = synchronized(lock) {
        if (mapStore.find(id) == null) return Outcome.Failure(ErrorCode.NOT_FOUND, "That map no longer exists.")
        mapStore.remove(id)
        if (_loaded.value?.id == id) _loaded.value = null
        Outcome.Success(Unit)
    }

    private fun isDuplicate(floorId: String, name: String, exceptId: String?): Boolean =
        mapStore.forFloor(floorId).any { it.id != exceptId && it.name.equals(name, ignoreCase = true) }

    private fun wrongState(message: String) = Outcome.Failure(ErrorCode.WRONG_STATE, message)

    companion object {
        const val DEFAULT_TICK_MILLIS = 300L
        const val DEFAULT_CELLS_PER_STEP = 10
        const val MIN_FINISH_PERCENT = 30
        const val COMPLETE_PERCENT = 90
    }
}
