package com.sakarrobotics.installation.core.data

import com.sakarrobotics.installation.core.model.ErrorCode
import com.sakarrobotics.installation.core.model.Floor
import com.sakarrobotics.installation.core.model.Outcome
import com.sakarrobotics.installation.core.model.SimMap
import com.sakarrobotics.installation.core.repository.FloorRepository
import com.sakarrobotics.installation.core.storage.IdGenerator
import com.sakarrobotics.installation.core.storage.Json
import com.sakarrobotics.installation.core.storage.KeyValueStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal object NameRules {
    const val MAX_LENGTH = 40
    const val MAX_FLOORS = 20
    const val MAX_MAPS = 200

    /** Trims and collapses internal whitespace so "Ground   floor" and "Ground floor" are the same name. */
    fun normalize(raw: String): String = raw.trim().replace(Regex("\\s+"), " ")

    fun validate(raw: String, what: String): Outcome<String> {
        val name = normalize(raw)
        return when {
            name.isEmpty() -> Outcome.Failure(ErrorCode.INVALID_NAME, "Enter a $what name.")
            name.length > MAX_LENGTH ->
                Outcome.Failure(ErrorCode.INVALID_NAME, "The $what name must be $MAX_LENGTH characters or fewer.")
            name.any { it.isISOControl() } ->
                Outcome.Failure(ErrorCode.INVALID_NAME, "The $what name contains unsupported characters.")
            else -> Outcome.Success(name)
        }
    }
}

/**
 * Persisted list of saved SIMULATED maps, shared by the mapping and floor repositories so a floor
 * can refuse deletion while it still owns maps. Contains demo data only.
 */
class SimMapStore(private val store: KeyValueStore) {
    private val _maps = MutableStateFlow(
        Json.loadList(store, KEY, Array<SimMap>::class.java) { emptyList() }
    )
    val maps: StateFlow<List<SimMap>> = _maps.asStateFlow()

    fun find(id: String): SimMap? = _maps.value.firstOrNull { it.id == id }
    fun forFloor(floorId: String): List<SimMap> = _maps.value.filter { it.floorId == floorId }

    fun add(map: SimMap) = update { it + map }
    fun replaceAll(maps: List<SimMap>) = update { maps }
    fun replace(map: SimMap) = update { list -> list.map { if (it.id == map.id) map else it } }
    fun remove(id: String) = update { list -> list.filterNot { it.id == id } }

    private fun update(change: (List<SimMap>) -> List<SimMap>) {
        val next = change(_maps.value)
        _maps.value = next
        Json.save(store, KEY, next)
    }

    private companion object {
        const val KEY = "sim.maps.v1"
    }
}

class LocalFloorRepository(
    private val store: KeyValueStore,
    private val mapStore: SimMapStore,
    private val ids: IdGenerator
) : FloorRepository {

    private val _floors = MutableStateFlow(
        Json.loadList(store, KEY, Array<Floor>::class.java) { defaultFloors() }
            .sortedBy { it.level }
    )
    override val floors: StateFlow<List<Floor>> = _floors.asStateFlow()

    override fun addFloor(name: String): Outcome<Floor> {
        val valid = NameRules.validate(name, "floor")
        if (valid is Outcome.Failure) return valid
        val clean = (valid as Outcome.Success).value
        if (isDuplicate(clean, exceptId = null)) {
            return Outcome.Failure(ErrorCode.DUPLICATE_NAME, "A floor named \"$clean\" already exists.")
        }
        if (_floors.value.size >= NameRules.MAX_FLOORS) {
            return Outcome.Failure(ErrorCode.LIMIT_REACHED, "At most ${NameRules.MAX_FLOORS} floors are supported.")
        }
        val level = (_floors.value.maxOfOrNull { it.level } ?: -1) + 1
        val floor = Floor(ids.next("floor"), clean, level)
        commit(_floors.value + floor)
        return Outcome.Success(floor)
    }

    override fun renameFloor(id: String, name: String): Outcome<Floor> {
        val existing = _floors.value.firstOrNull { it.id == id }
            ?: return Outcome.Failure(ErrorCode.NOT_FOUND, "That floor no longer exists.")
        val valid = NameRules.validate(name, "floor")
        if (valid is Outcome.Failure) return valid
        val clean = (valid as Outcome.Success).value
        if (isDuplicate(clean, exceptId = id)) {
            return Outcome.Failure(ErrorCode.DUPLICATE_NAME, "A floor named \"$clean\" already exists.")
        }
        val updated = existing.copy(name = clean)
        commit(_floors.value.map { if (it.id == id) updated else it })
        return Outcome.Success(updated)
    }

    override fun deleteFloor(id: String): Outcome<Unit> {
        val existing = _floors.value.firstOrNull { it.id == id }
            ?: return Outcome.Failure(ErrorCode.NOT_FOUND, "That floor no longer exists.")
        val count = mapStore.forFloor(id).size
        if (count > 0) {
            return Outcome.Failure(
                ErrorCode.FLOOR_HAS_MAPS,
                "\"${existing.name}\" still has $count saved simulated map(s). Delete them first."
            )
        }
        commit(_floors.value.filterNot { it.id == id })
        return Outcome.Success(Unit)
    }

    /** Replaces the whole floor list (backup restore). Callers validate first. */
    fun replaceAll(next: List<Floor>) = commit(next)

    /** Back to the two default floors (reset demo data). */
    fun resetToDefaults() = commit(defaultFloors())

    private fun isDuplicate(name: String, exceptId: String?): Boolean =
        _floors.value.any { it.id != exceptId && it.name.equals(name, ignoreCase = true) }

    private fun commit(next: List<Floor>) {
        val sorted = next.sortedBy { it.level }
        _floors.value = sorted
        Json.save(store, KEY, sorted)
    }

    private companion object {
        const val KEY = "floors.v1"
        fun defaultFloors() = listOf(
            Floor("floor-ground", "Ground floor", 0),
            Floor("floor-first", "First floor", 1)
        )
    }
}
