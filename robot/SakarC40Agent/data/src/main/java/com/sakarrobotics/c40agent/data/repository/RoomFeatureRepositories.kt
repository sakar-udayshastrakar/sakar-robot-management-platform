package com.sakarrobotics.c40agent.data.repository

import com.sakarrobotics.c40agent.data.db.ConsumableDao
import com.sakarrobotics.c40agent.data.db.ConsumableEntity
import com.sakarrobotics.c40agent.data.db.RouteDao
import com.sakarrobotics.c40agent.data.db.RouteEntity
import com.sakarrobotics.c40agent.data.db.ScheduleTaskDao
import com.sakarrobotics.c40agent.data.db.ScheduleTaskEntity
import com.sakarrobotics.c40agent.domain.model.Capability
import com.sakarrobotics.c40agent.domain.model.CleaningIntensity
import com.sakarrobotics.c40agent.domain.model.CleaningMode
import com.sakarrobotics.c40agent.domain.model.ConsumableItem
import com.sakarrobotics.c40agent.domain.model.ConsumableType
import com.sakarrobotics.c40agent.domain.model.RouteRecord
import com.sakarrobotics.c40agent.domain.model.RouteRecordingState
import com.sakarrobotics.c40agent.domain.model.ScheduleRepeat
import com.sakarrobotics.c40agent.domain.model.ScheduleTask
import com.sakarrobotics.c40agent.domain.model.SyncState
import com.sakarrobotics.c40agent.domain.model.Weekday
import com.sakarrobotics.c40agent.domain.repository.ConsumableRepository
import com.sakarrobotics.c40agent.domain.repository.RouteRepository
import com.sakarrobotics.c40agent.domain.repository.ScheduleRepository
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

class RoomScheduleRepository(private val dao: ScheduleTaskDao) : ScheduleRepository {

    override val schedules: Flow<List<ScheduleTask>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun upsert(task: ScheduleTask) = dao.upsert(task.toEntity())

    override suspend fun delete(taskId: String) = dao.delete(taskId)

    override suspend fun setEnabled(taskId: String, enabled: Boolean) = dao.setEnabled(taskId, enabled)
}

private fun ScheduleTaskEntity.toDomain() = ScheduleTask(
    id = id,
    name = name,
    enabled = enabled,
    startTime = startTime,
    endTime = endTime,
    repeat = ScheduleRepeat.valueOf(repeat),
    days = days.split(",").filter { it.isNotBlank() }.map { Weekday.valueOf(it) }.toSet(),
    cleaningCycles = cleaningCycles,
    zoneIds = zoneIds.split(",").filter { it.isNotBlank() },
    mode = CleaningMode.valueOf(mode),
    intensity = CleaningIntensity.valueOf(intensity),
    remoteId = remoteId,
    syncState = SyncState.valueOf(syncState)
)

private fun ScheduleTask.toEntity() = ScheduleTaskEntity(
    id = id, name = name, enabled = enabled, startTime = startTime, endTime = endTime,
    repeat = repeat.name, days = days.joinToString(",") { it.name }, cleaningCycles = cleaningCycles,
    zoneIds = zoneIds.joinToString(","), mode = mode.name, intensity = intensity.name,
    remoteId = remoteId, syncState = syncState.name
)

/**
 * Locally-persisted, operator-tracked wear (see ConsumableItem's docs -
 * never sourced from a wear sensor, because the vendored SDK exposes
 * none). Seeds the same consumable set shown in the reference app on
 * first run so the screen is never empty, then all mutation is real
 * local state from there on.
 */
class RoomConsumableRepository(private val dao: ConsumableDao) : ConsumableRepository {

    override val consumables: Flow<List<ConsumableItem>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun seedIfEmpty() {
        dao.insertIfAbsent(
            listOf(
                ConsumableEntity("side_brush", ConsumableType.SIDE_BRUSH.name, "Side Brush", 500, 0),
                ConsumableEntity("sweeping_brush", ConsumableType.SWEEPING_BRUSH.name, "Sweeping Brush", 1000, 0),
                ConsumableEntity("fibre_brush", ConsumableType.FIBRE_BRUSH.name, "Fibre Brush", 1000, 0),
                ConsumableEntity("washing_brush", ConsumableType.WASHING_BRUSH.name, "Washing Brush", 1000, 0),
                ConsumableEntity("dust_mop_brush", ConsumableType.DUST_MOP_BRUSH.name, "Dust Mop Brush", 600, 0),
                ConsumableEntity("squeegee_blade", ConsumableType.SQUEEGEE_BLADE.name, "Squeegee Blade", 500, 0),
                ConsumableEntity("hepa_filter", ConsumableType.HEPA_FILTER.name, "HEPA Filter", 1000, 0),
                ConsumableEntity("dust_bag", ConsumableType.DUST_BAG.name, "Dust Bag", 1000, 0)
            )
        )
    }

    override suspend fun recordUsageHours(id: String, additionalHours: Int) {
        val entity = dao.get(id) ?: return
        dao.update(entity.copy(usedHours = (entity.usedHours + additionalHours).coerceAtMost(entity.lifespanHours)))
    }

    override suspend fun resetUsage(id: String) {
        val entity = dao.get(id) ?: return
        dao.update(entity.copy(usedHours = 0))
    }
}

private fun ConsumableEntity.toDomain() = ConsumableItem(
    id = id, type = ConsumableType.valueOf(type), displayName = displayName,
    lifespanHours = lifespanHours, usedHours = usedHours
)

/**
 * Teach-route metadata. The vendored SDK has no teach/record API (see
 * RouteRecord's docs) - recording only measures real wall-clock duration
 * and, if [readPosition] ever confirms a real API on a physical robot,
 * would sample real points; on every currently-supported device
 * [pointCount] stays 0, honestly, rather than fabricating a path.
 */
class RoomRouteRepository(private val dao: RouteDao) : RouteRepository {

    override val routes: Flow<List<RouteRecord>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    private val _recordingState = MutableStateFlow(RouteRecordingState.IDLE)
    override val recordingState: Flow<RouteRecordingState> = _recordingState.asStateFlow()

    private var recordingName: String = ""
    private var recordingStartedAt: Long = 0

    override suspend fun startRecording(name: String) {
        recordingName = name
        recordingStartedAt = System.currentTimeMillis()
        _recordingState.value = RouteRecordingState.RECORDING
    }

    override suspend fun pauseRecording() {
        if (_recordingState.value == RouteRecordingState.RECORDING) _recordingState.value = RouteRecordingState.PAUSED
    }

    override suspend fun resumeRecording() {
        if (_recordingState.value == RouteRecordingState.PAUSED) _recordingState.value = RouteRecordingState.RECORDING
    }

    override suspend fun stopAndSaveRecording(): RouteRecord {
        val durationSeconds = ((System.currentTimeMillis() - recordingStartedAt) / 1000L).coerceAtLeast(0)
        _recordingState.value = RouteRecordingState.IDLE
        val record = RouteRecord(
            id = UUID.randomUUID().toString(),
            name = recordingName.ifBlank { "Route ${System.currentTimeMillis()}" },
            createdAtMillis = System.currentTimeMillis(),
            durationSeconds = durationSeconds,
            pointCount = 0,
            capability = Capability.LOCAL
        )
        dao.upsert(record.toEntity())
        return record
    }

    override suspend fun rename(routeId: String, newName: String) = dao.rename(routeId, newName)

    override suspend fun delete(routeId: String) = dao.delete(routeId)

    override suspend fun playback(routeId: String): Result<Unit> =
        Result.failure(UnsupportedOperationException(
            "Route playback requires a path-following navigation API the vendored Peanut SDK does not expose."))
}

private fun RouteEntity.toDomain() = RouteRecord(
    id = id, name = name, createdAtMillis = createdAtMillis, durationSeconds = durationSeconds,
    pointCount = pointCount, capability = Capability.valueOf(capability)
)

private fun RouteRecord.toEntity() = RouteEntity(
    id = id, name = name, createdAtMillis = createdAtMillis, durationSeconds = durationSeconds,
    pointCount = pointCount, capability = capability.name
)
