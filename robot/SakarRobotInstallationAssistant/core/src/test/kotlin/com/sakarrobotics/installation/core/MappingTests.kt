package com.sakarrobotics.installation.core

import com.sakarrobotics.installation.core.data.SimulatedBackend
import com.sakarrobotics.installation.core.data.SimulatedMappingRepository
import com.sakarrobotics.installation.core.model.BackendMode
import com.sakarrobotics.installation.core.model.CellState
import com.sakarrobotics.installation.core.model.ErrorCode
import com.sakarrobotics.installation.core.model.MapStatus
import com.sakarrobotics.installation.core.model.MappingPhase
import com.sakarrobotics.installation.core.model.Outcome
import com.sakarrobotics.installation.core.sim.MappingSimulation
import com.sakarrobotics.installation.core.sim.SimulatedFloorPlan
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MappingSimulationTest {

    @Test
    fun `floor plan is deterministic per floor and differs between floors`() {
        val a1 = SimulatedFloorPlan.generate("floor-ground")
        val a2 = SimulatedFloorPlan.generate("floor-ground")
        val b = SimulatedFloorPlan.generate("floor-first")
        assertEquals(a1, a2)
        assertNotEquals(a1, b)
        assertEquals(SimulatedFloorPlan.WIDTH, a1.width)
        assertEquals(SimulatedFloorPlan.HEIGHT, a1.height)
        assertEquals(0, a1.count(CellState.UNKNOWN), "ground truth never contains unknown cells")
    }

    @Test
    fun `progress only increases and reaches one hundred percent`() {
        val sim = MappingSimulation(SimulatedFloorPlan.generate("floor-ground"), cellsPerStep = 10)
        var last = -1
        var guard = 0
        while (!sim.isComplete && guard++ < 10_000) {
            sim.step()
            assertTrue(sim.progressPercent >= last)
            last = sim.progressPercent
        }
        assertTrue(sim.isComplete)
        assertEquals(100, sim.progressPercent)
    }

    @Test
    fun `snapshot reveals nothing it has not reached yet`() {
        val truth = SimulatedFloorPlan.generate("floor-ground")
        val sim = MappingSimulation(truth, cellsPerStep = 10)
        val before = sim.snapshot()
        assertEquals(0, before.count(CellState.FREE))
        sim.step()
        val after = sim.snapshot()
        assertEquals(10, after.count(CellState.FREE))
        assertTrue(after.count(CellState.UNKNOWN) > 0)
        // Every revealed cell agrees with the ground truth.
        for (y in 0 until after.height) for (x in 0 until after.width) {
            if (after[x, y] != CellState.UNKNOWN) assertEquals(truth[x, y], after[x, y])
        }
    }

    @Test
    fun `a fully revealed map contains every free cell`() {
        val truth = SimulatedFloorPlan.generate("floor-first")
        val sim = MappingSimulation(truth, 25)
        while (sim.step()) { /* sweep */ }
        assertEquals(truth.count(CellState.FREE), sim.snapshot().count(CellState.FREE))
    }
}

class MappingRepositoryTest {

    private val ground = "floor-ground"

    @Test
    fun `backend is identified as simulated`() {
        val f = Fixture()
        assertEquals(BackendMode.SIMULATED, f.mapping.backend.mode)
        assertEquals(SimulatedBackend.info, f.mapping.backend)
        assertTrue(f.mapping.backend.label.contains("SIMULATED"))
    }

    @Test
    fun `full lifecycle start pause resume finish save`() {
        val f = Fixture()
        assertTrue(f.mapping.startSession(ground).isSuccess)
        assertEquals(MappingPhase.MAPPING, f.mapping.session.value.phase)
        assertTrue(f.mapping.session.value.message!!.contains("No robot is mapping"))

        f.mapping.advance(5)
        val progressAtPause = f.mapping.session.value.progressPercent
        assertTrue(progressAtPause > 0)

        assertTrue(f.mapping.pause().isSuccess)
        f.mapping.advance(10) // ignored while paused
        assertEquals(progressAtPause, f.mapping.session.value.progressPercent)

        assertTrue(f.mapping.resume().isSuccess)
        while (f.mapping.session.value.progressPercent < 40) f.mapping.advance(1)
        assertTrue(f.mapping.finish().isSuccess)
        assertEquals(MappingPhase.FINISHED, f.mapping.session.value.phase)

        val saved = f.mapping.saveMap("  Main hall  ").getOrNull()
        assertNotNull(saved)
        assertEquals("Main hall", saved!!.name)
        assertEquals(ground, saved.floorId)
        assertEquals(MapStatus.PARTIAL, saved.status)
        assertTrue(saved.simulated)
        assertEquals(MappingPhase.IDLE, f.mapping.session.value.phase)
        assertEquals(listOf(saved), f.mapping.maps.value)
        assertEquals(saved, f.mapping.loadedMap.value)
    }

    @Test
    fun `full coverage finishes automatically and saves as complete`() {
        val f = Fixture()
        f.mapFloor(ground, "Everything")
        assertEquals(MapStatus.COMPLETE, f.mapping.maps.value.single().status)
        assertEquals(100, f.mapping.maps.value.single().coveragePercent)
    }

    @Test
    fun `finish is refused before enough has been scanned`() {
        val f = Fixture()
        f.mapping.startSession(ground)
        f.mapping.advance(1)
        val failure = f.mapping.finish().failureOrNull()
        assertEquals(ErrorCode.INSUFFICIENT_COVERAGE, failure?.code)
        assertEquals(MappingPhase.MAPPING, f.mapping.session.value.phase)
    }

    @Test
    fun `invalid state transitions are rejected with clear errors`() {
        val f = Fixture()
        assertEquals(ErrorCode.WRONG_STATE, f.mapping.pause().failureOrNull()?.code)
        assertEquals(ErrorCode.WRONG_STATE, f.mapping.resume().failureOrNull()?.code)
        assertEquals(ErrorCode.WRONG_STATE, f.mapping.finish().failureOrNull()?.code)
        assertEquals(ErrorCode.WRONG_STATE, f.mapping.cancel().failureOrNull()?.code)
        assertEquals(ErrorCode.WRONG_STATE, f.mapping.saveMap("x").failureOrNull()?.code)
        f.mapping.startSession(ground)
        assertEquals(ErrorCode.WRONG_STATE, f.mapping.startSession(ground).failureOrNull()?.code)
        assertEquals(ErrorCode.WRONG_STATE, f.mapping.resume().failureOrNull()?.code)
        assertEquals(ErrorCode.WRONG_STATE, f.mapping.saveMap("x").failureOrNull()?.code)
    }

    @Test
    fun `starting needs an existing floor`() {
        val f = Fixture()
        assertEquals(ErrorCode.NOT_FOUND, f.mapping.startSession("no-such-floor").failureOrNull()?.code)
        assertEquals(MappingPhase.IDLE, f.mapping.session.value.phase)
    }

    @Test
    fun `cancel discards the session and saves nothing`() {
        val f = Fixture()
        f.mapping.startSession(ground)
        f.mapping.advance(3)
        assertTrue(f.mapping.cancel().isSuccess)
        assertEquals(MappingPhase.CANCELLED, f.mapping.session.value.phase)
        assertNull(f.mapping.session.value.grid)
        assertTrue(f.mapping.maps.value.isEmpty())
        assertTrue(f.mapping.session.value.message!!.contains("Nothing was saved"))
    }

    @Test
    fun `map names are validated and unique per floor`() {
        val f = Fixture()
        f.mapFloor(ground, "Lobby")
        f.mapping.startSession(ground)
        while (f.mapping.session.value.phase == MappingPhase.MAPPING) f.mapping.advance(1)
        assertEquals(ErrorCode.INVALID_NAME, f.mapping.saveMap("   ").failureOrNull()?.code)
        assertEquals(ErrorCode.INVALID_NAME, f.mapping.saveMap("x".repeat(41)).failureOrNull()?.code)
        assertEquals(ErrorCode.DUPLICATE_NAME, f.mapping.saveMap("lobby").failureOrNull()?.code)
        // Same name on a different floor is allowed.
        f.mapping.resetSession()
        f.mapFloor("floor-first", "Lobby")
        assertEquals(2, f.mapping.maps.value.size)
    }

    @Test
    fun `saved maps can be loaded renamed and deleted`() {
        val f = Fixture()
        f.mapFloor(ground, "One")
        f.mapFloor("floor-first", "Two")
        val one = f.mapping.maps.value.first { it.name == "One" }

        f.mapping.closeLoadedMap()
        assertTrue(f.mapping.loadMap(one.id).isSuccess)
        assertEquals(one, f.mapping.loadedMap.value)

        val renamed = f.mapping.renameMap(one.id, "Renamed").getOrNull()
        assertEquals("Renamed", renamed?.name)
        assertEquals("Renamed", f.mapping.loadedMap.value?.name)
        assertEquals(ErrorCode.DUPLICATE_NAME, run {
            f.mapFloor(ground, "Other")
            f.mapping.renameMap(f.mapping.maps.value.first { it.name == "Other" }.id, "renamed").failureOrNull()?.code
        })

        assertTrue(f.mapping.loadMap(one.id).isSuccess)
        assertTrue(f.mapping.deleteMap(one.id).isSuccess)
        assertNull(f.mapping.loadedMap.value, "deleting the opened map closes it")
        assertEquals(ErrorCode.NOT_FOUND, f.mapping.deleteMap(one.id).failureOrNull()?.code)
        assertEquals(ErrorCode.NOT_FOUND, f.mapping.loadMap(one.id).failureOrNull()?.code)
        assertEquals(ErrorCode.NOT_FOUND, f.mapping.renameMap(one.id, "x").failureOrNull()?.code)
    }

    @Test
    fun `opening a saved map is refused while a session is running`() {
        val f = Fixture()
        f.mapFloor(ground, "Saved")
        val saved = f.mapping.maps.value.single()
        f.mapping.startSession("floor-first")
        assertEquals(ErrorCode.WRONG_STATE, f.mapping.loadMap(saved.id).failureOrNull()?.code)
    }

    @Test
    fun `saved maps survive recreating the services`() {
        val f1 = Fixture()
        f1.mapFloor(ground, "Persisted")
        val f2 = Fixture(store = f1.store)
        val map = f2.mapping.maps.value.single()
        assertEquals("Persisted", map.name)
        assertEquals(f1.mapping.maps.value.single().preview, map.preview)
        assertEquals(MappingPhase.IDLE, f2.mapping.session.value.phase, "an interrupted session is never restored")
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `timer drives progress and pause stops it`() = runTest {
        val f = Fixture(scope = this, tickMillis = 100)
        f.mapping.startSession(ground)
        advanceTimeBy(350)
        val running = f.mapping.session.value.progressPercent
        assertTrue(running > 0)

        f.mapping.pause()
        advanceTimeBy(1_000)
        assertEquals(running, f.mapping.session.value.progressPercent)

        f.mapping.resume()
        advanceTimeBy(350)
        assertTrue(f.mapping.session.value.progressPercent > running)
        f.mapping.cancel()
    }
}
