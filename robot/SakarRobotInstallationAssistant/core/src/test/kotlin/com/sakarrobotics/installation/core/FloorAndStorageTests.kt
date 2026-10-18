package com.sakarrobotics.installation.core

import com.sakarrobotics.installation.core.hardware.DisabledRobotHardwarePort
import com.sakarrobotics.installation.core.model.ErrorCode
import com.sakarrobotics.installation.core.storage.InMemoryKeyValueStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FloorRepositoryTest {

    @Test
    fun `starts with two default floors in level order`() {
        val f = Fixture()
        assertEquals(listOf("Ground floor", "First floor"), f.floors.floors.value.map { it.name })
        assertEquals(listOf(0, 1), f.floors.floors.value.map { it.level })
    }

    @Test
    fun `floors can be added renamed and deleted`() {
        val f = Fixture()
        val added = f.floors.addFloor(" Mezzanine ").getOrNull()!!
        assertEquals("Mezzanine", added.name)
        assertEquals(2, added.level)
        assertEquals(3, f.floors.floors.value.size)

        assertEquals("Roof", f.floors.renameFloor(added.id, "Roof").getOrNull()?.name)
        assertTrue(f.floors.deleteFloor(added.id).isSuccess)
        assertEquals(2, f.floors.floors.value.size)
    }

    @Test
    fun `floor names are validated and unique`() {
        val f = Fixture()
        assertEquals(ErrorCode.INVALID_NAME, f.floors.addFloor("").failureOrNull()?.code)
        assertEquals(ErrorCode.DUPLICATE_NAME, f.floors.addFloor("ground FLOOR").failureOrNull()?.code)
        val first = f.floors.floors.value.first { it.name == "First floor" }
        assertEquals(ErrorCode.DUPLICATE_NAME, f.floors.renameFloor(first.id, "Ground floor").failureOrNull()?.code)
        assertEquals(ErrorCode.NOT_FOUND, f.floors.renameFloor("nope", "X").failureOrNull()?.code)
        assertEquals(ErrorCode.NOT_FOUND, f.floors.deleteFloor("nope").failureOrNull()?.code)
    }

    @Test
    fun `a floor with saved maps cannot be deleted until the maps are`() {
        val f = Fixture()
        f.mapFloor("floor-ground", "Hall")
        assertEquals(ErrorCode.FLOOR_HAS_MAPS, f.floors.deleteFloor("floor-ground").failureOrNull()?.code)
        f.mapping.deleteMap(f.mapping.maps.value.single().id)
        assertTrue(f.floors.deleteFloor("floor-ground").isSuccess)
    }

    @Test
    fun `multiple floors keep separate maps`() {
        val f = Fixture()
        f.mapFloor("floor-ground", "A")
        f.mapFloor("floor-first", "B")
        val byFloor = f.mapping.maps.value.groupBy { it.floorId }
        assertEquals(setOf("floor-ground", "floor-first"), byFloor.keys)
    }

    @Test
    fun `floors persist`() {
        val f1 = Fixture()
        f1.floors.addFloor("Basement")
        val f2 = Fixture(store = f1.store)
        assertTrue(f2.floors.floors.value.any { it.name == "Basement" })
    }
}

class StorageResilienceTest {

    @Test
    fun `corrupt stored data falls back to defaults instead of crashing`() {
        val store = InMemoryKeyValueStore()
        store.put("floors.v1", "{{{ not json")
        store.put("sim.maps.v1", "[1,2,3")
        store.put("installation.v1", "garbage")
        store.put("reports.v1", "")
        store.put("diag.scenario.v1", "\"NOT_A_SCENARIO\"")
        val f = Fixture(store = store)
        assertEquals(2, f.floors.floors.value.size)
        assertTrue(f.mapping.maps.value.isEmpty())
        assertEquals(null, f.installations.current.value)
        assertTrue(f.reports.reports.value.isEmpty())
        assertTrue(f.diagnostics.scenario.value != null)
    }

    @Test
    fun `no credential or personal field exists in any stored value`() {
        val f = Fixture()
        f.floors.addFloor("Extra") // floors are only written once they change
        f.mapFloor("floor-ground", "Hall")
        f.installations.ensureCurrent()
        f.services.generateReport()
        val keys = listOf("floors.v1", "sim.maps.v1", "installation.v1", "reports.v1")
        keys.forEach { key ->
            val raw = checkNotNull(f.store.get(key)) { "$key was not persisted" }.lowercase()
            listOf("password", "token", "secret", "email", "phone", "customer").forEach {
                assertFalse(raw.contains(it), "$key must not contain \"$it\"")
            }
        }
    }
}

class HardwarePortTest {
    @Test
    fun `the only hardware port is permanently disabled`() {
        assertFalse(DisabledRobotHardwarePort.enabled)
        assertTrue(DisabledRobotHardwarePort.disabledReason.contains("simulation only", ignoreCase = true))
    }
}
