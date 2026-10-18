package com.sakarrobotics.installation.core

import com.sakarrobotics.installation.core.data.NameRules
import com.sakarrobotics.installation.core.model.ChecklistState
import com.sakarrobotics.installation.core.model.ErrorCode
import com.sakarrobotics.installation.core.model.MappingPhase
import com.sakarrobotics.installation.core.model.Outcome
import com.sakarrobotics.installation.core.storage.InMemoryKeyValueStore
import com.sakarrobotics.installation.core.storage.KeyValueStore
import com.sakarrobotics.installation.core.storage.StorageException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private const val GROUND = "floor-ground"

/** A store whose writes can be switched to fail, like a full or read-only disk. */
private class FlakyStore(private val inner: KeyValueStore = InMemoryKeyValueStore()) : KeyValueStore {
    var failWrites = false
    override fun get(key: String) = inner.get(key)
    override fun put(key: String, value: String) {
        if (failWrites) throw StorageException("disk full")
        inner.put(key, value)
    }
    override fun remove(key: String) {
        if (failWrites) throw StorageException("disk full")
        inner.remove(key)
    }
}

class PersistenceErrorTest {

    @Test
    fun `a failing disk never crashes a repository and the in-memory state stays usable`() {
        val store = FlakyStore()
        val f = Fixture(store = store)
        store.failWrites = true

        val added = f.floors.addFloor("Mezzanine")
        assertTrue(added is Outcome.Success)
        assertTrue(f.floors.floors.value.any { it.name == "Mezzanine" })
        assertNotNull(f.services.persistence.error.value)
        assertTrue(f.services.persistence.error.value!!.contains("disk full"))
        assertTrue(f.services.persistence.error.value!!.contains("may be lost"))
    }

    @Test
    fun `the error clears when the same data is written successfully again`() {
        val store = FlakyStore()
        val f = Fixture(store = store)
        store.failWrites = true
        f.floors.addFloor("Mezzanine")
        assertNotNull(f.services.persistence.error.value)

        store.failWrites = false
        f.floors.addFloor("Roof")
        assertNull(f.services.persistence.error.value)
    }

    @Test
    fun `an unrelated successful write does not hide a failure that is still unresolved`() {
        val store = FlakyStore()
        val f = Fixture(store = store)
        store.failWrites = true
        f.floors.addFloor("Mezzanine")          // floors key fails
        store.failWrites = false
        f.installations.startNew()              // installation key succeeds
        assertNotNull(f.services.persistence.error.value, "the floors write is still lost")
    }

    @Test
    fun `the user can dismiss the warning`() {
        val store = FlakyStore().also { it.failWrites = true }
        val f = Fixture(store = store)
        f.floors.addFloor("Mezzanine")
        f.services.persistence.dismiss()
        assertNull(f.services.persistence.error.value)
    }

    @Test
    fun `a failed backup restore write is reported not thrown`() {
        val json = Fixture().also { it.mapFloor(GROUND, "Lobby") }.services.backup.export("x")
        val store = FlakyStore()
        val target = Fixture(store = store)
        store.failWrites = true
        assertTrue(target.services.backup.restore(json, com.sakarrobotics.installation.core.backup.RestoreMode.REPLACE) is Outcome.Success)
        assertNotNull(target.services.persistence.error.value)
    }
}

class WorkflowPersistenceTest {

    @Test
    fun `wizard progress checklist and selections survive an app restart`() {
        val store = InMemoryKeyValueStore()
        val first = Fixture(store = store)
        val inst = first.installations.startNew()
        first.installations.acknowledgeOverview()
        first.installations.setSelectedFloors(listOf(GROUND))
        first.installations.updateChecklist("charger", ChecklistState.DONE, "Near the lobby")
        first.mapFloor(GROUND, "Lobby")

        val second = Fixture(store = store)
        val restored = second.installations.current.value!!
        assertEquals(inst.id, restored.id)
        assertTrue(restored.overviewAcknowledged)
        assertEquals(listOf(GROUND), restored.selectedFloorIds)
        assertEquals(ChecklistState.DONE, restored.checklist.single { it.id == "charger" }.state)
        assertEquals("Near the lobby", restored.checklist.single { it.id == "charger" }.note)
        assertEquals("Lobby", second.mapping.maps.value.single().name)
    }

    @Test
    fun `corrupted stored data falls back to defaults instead of crashing`() {
        val store = InMemoryKeyValueStore()
        listOf("floors.v1", "sim.maps.v1", "installation.v1", "reports.v1", "diag.scenario.v1", "diag.latest.v1")
            .forEach { store.put(it, "{{{ not json") }
        val f = Fixture(store = store)
        assertEquals(2, f.floors.floors.value.size)
        assertTrue(f.mapping.maps.value.isEmpty())
        assertNull(f.installations.current.value)
        assertTrue(f.reports.reports.value.isEmpty())
    }
}

class InputValidationTest {

    @Test
    fun `names are trimmed and internal whitespace is collapsed`() {
        assertEquals("Ground floor", NameRules.normalize("  Ground    floor \t"))
    }

    @Test
    fun `duplicate floor names are caught regardless of case and spacing`() {
        val f = Fixture()
        val result = f.floors.addFloor("  ground   FLOOR ")
        assertEquals(ErrorCode.DUPLICATE_NAME, (result as Outcome.Failure).code)
    }

    @Test
    fun `rename to the same name with different spacing is a duplicate of another floor only`() {
        val f = Fixture()
        val first = f.floors.floors.value.first()
        assertTrue(f.floors.renameFloor(first.id, "Ground   floor").isSuccess, "renaming a floor to itself is allowed")
        assertEquals(ErrorCode.DUPLICATE_NAME, (f.floors.renameFloor(first.id, "first  floor") as Outcome.Failure).code)
    }

    @Test
    fun `blank and control-character names are rejected`() {
        val f = Fixture()
        assertEquals(ErrorCode.INVALID_NAME, (f.floors.addFloor("   ") as Outcome.Failure).code)
        assertEquals(ErrorCode.INVALID_NAME, (f.floors.addFloor("Bad\u0000Name") as Outcome.Failure).code)
        assertEquals(ErrorCode.INVALID_NAME, (f.floors.addFloor("x".repeat(NameRules.MAX_LENGTH + 1)) as Outcome.Failure).code)
    }

    @Test
    fun `the number of floors is limited`() {
        val f = Fixture()
        repeat(NameRules.MAX_FLOORS - 2) { assertTrue(f.floors.addFloor("Extra $it").isSuccess) }
        assertEquals(ErrorCode.LIMIT_REACHED, (f.floors.addFloor("One too many") as Outcome.Failure).code)
    }

    @Test
    fun `a map name that only differs by case or spacing is a duplicate on the same floor`() {
        val f = Fixture()
        f.mapFloor(GROUND, "Lobby sweep")
        f.mapping.startSession(GROUND)
        while (f.mapping.session.value.phase == MappingPhase.MAPPING) f.mapping.advance(1)
        val result = f.mapping.saveMap("  lobby   SWEEP ")
        assertEquals(ErrorCode.DUPLICATE_NAME, (result as Outcome.Failure).code)
        assertEquals(1, f.mapping.maps.value.size)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class SimulationTimerCleanupTest {

    @Test
    fun `interrupt pauses a running session and its timer stops`() = runTest {
        val f = Fixture(scope = this, tickMillis = 100)
        f.mapping.startSession(GROUND)
        advanceTimeBy(350)
        val before = f.mapping.session.value.progressPercent
        assertTrue(before > 0)

        f.mapping.interrupt("Paused because you left the workspace.")
        assertEquals(MappingPhase.PAUSED, f.mapping.session.value.phase)
        assertEquals("Paused because you left the workspace.", f.mapping.session.value.message)

        advanceTimeBy(5_000)
        assertEquals(before, f.mapping.session.value.progressPercent, "no background ticking after interrupt")

        f.mapping.resume()
        advanceTimeBy(350)
        assertTrue(f.mapping.session.value.progressPercent > before, "resume continues from where it paused")
        f.mapping.cancel()
    }

    @Test
    fun `interrupt does nothing when there is no running session`() = runTest {
        val f = Fixture(scope = this, tickMillis = 100)
        f.mapping.interrupt("x")
        assertEquals(MappingPhase.IDLE, f.mapping.session.value.phase)
        f.mapping.startSession(GROUND)
        f.mapping.pause()
        f.mapping.interrupt("x")
        assertEquals("Simulated session paused.", f.mapping.session.value.message, "an already paused session is untouched")
        f.mapping.cancel()
    }

    @Test
    fun `close stops the timer and keeps the session state`() = runTest {
        val f = Fixture(scope = this, tickMillis = 100)
        f.mapping.startSession(GROUND)
        advanceTimeBy(350)
        val before = f.mapping.session.value.progressPercent

        f.services.close()
        advanceTimeBy(5_000)
        assertEquals(before, f.mapping.session.value.progressPercent)
        f.services.close() // idempotent
        f.mapping.cancel()
    }

    @Test
    fun `finish cancel and a completed scan all stop the timer`() = runTest {
        val f = Fixture(scope = this, tickMillis = 100)

        f.mapping.startSession(GROUND)
        advanceTimeBy(2_500)
        assertTrue(f.mapping.finish().isSuccess)
        val finished = f.mapping.session.value.progressPercent
        advanceTimeBy(5_000)
        assertEquals(finished, f.mapping.session.value.progressPercent)
        f.mapping.cancel()

        f.mapping.startSession(GROUND)
        advanceTimeBy(100_000) // long enough to cover the whole floor plan
        assertEquals(MappingPhase.FINISHED, f.mapping.session.value.phase)
        val done = f.mapping.session.value.progressPercent
        advanceTimeBy(10_000)
        assertEquals(done, f.mapping.session.value.progressPercent)
        f.mapping.cancel()
    }

    @Test
    fun `cancel discards progress and nothing is saved`() = runTest {
        val f = Fixture(scope = this, tickMillis = 100)
        f.mapping.startSession(GROUND)
        advanceTimeBy(500)
        f.mapping.cancel()
        advanceTimeBy(2_000)
        assertEquals(MappingPhase.CANCELLED, f.mapping.session.value.phase)
        assertEquals(0, f.mapping.session.value.progressPercent)
        assertTrue(f.mapping.maps.value.isEmpty())
    }
}

class ResetDemoDataTest {

    @Test
    fun `reset returns every repository to its defaults and removes the saved files`() {
        val store = InMemoryKeyValueStore()
        val f = Fixture(store = store)
        f.floors.addFloor("Roof")
        f.installations.startNew()
        f.mapFloor(GROUND, "Lobby")
        f.services.generateReport()
        f.diagnostics.setScenario(com.sakarrobotics.installation.core.model.SimulationScenario.DEGRADED)

        f.services.backup.resetDemoData()

        assertEquals(listOf("Ground floor", "First floor"), f.floors.floors.value.map { it.name })
        assertTrue(f.mapping.maps.value.isEmpty())
        assertNull(f.installations.current.value)
        assertTrue(f.reports.reports.value.isEmpty())
        assertEquals(com.sakarrobotics.installation.core.model.SimulationScenario.HEALTHY, f.diagnostics.scenario.value)
        assertNull(f.diagnostics.latestSensors.value)

        val restarted = Fixture(store = store)
        assertEquals(2, restarted.floors.floors.value.size)
        assertTrue(restarted.mapping.maps.value.isEmpty())
        assertNull(restarted.installations.current.value)
        assertTrue(restarted.reports.reports.value.isEmpty())
    }

    @Test
    fun `reset stops a running simulated session`() {
        val f = Fixture()
        f.mapping.startSession(GROUND)
        f.services.backup.resetDemoData()
        assertFalse(f.mapping.session.value.isActive)
        assertEquals(MappingPhase.IDLE, f.mapping.session.value.phase)
    }
}
