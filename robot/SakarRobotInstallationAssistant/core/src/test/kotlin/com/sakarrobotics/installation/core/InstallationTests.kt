package com.sakarrobotics.installation.core

import com.sakarrobotics.installation.core.logic.ReportBuilder
import com.sakarrobotics.installation.core.logic.SimulationNotice
import com.sakarrobotics.installation.core.logic.ValidationLogic
import com.sakarrobotics.installation.core.logic.WizardLogic
import com.sakarrobotics.installation.core.model.ChecklistState
import com.sakarrobotics.installation.core.model.DiagnosticState
import com.sakarrobotics.installation.core.model.ErrorCode
import com.sakarrobotics.installation.core.model.SimulationScenario
import com.sakarrobotics.installation.core.model.StepState
import com.sakarrobotics.installation.core.model.WizardStep
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DiagnosticsRepositoryTest {

    @Test
    fun `sensor rows cover every required sensor and are all simulated`() = runBlocking {
        val f = Fixture()
        val run = f.diagnostics.runSensors()
        assertEquals(
            listOf("LiDAR", "Depth / stereo camera", "Ultrasonic sensors", "IMU", "Localization", "Mapping service"),
            run.items.map { it.title }
        )
        assertTrue(run.items.all { it.simulated })
        assertTrue(run.items.all { it.message.startsWith("Simulated") })
        assertTrue(run.items.all { it.troubleshooting.isNotEmpty() })
        assertEquals(DiagnosticState.OK, run.worst)
    }

    @Test
    fun `degraded scenario shows warnings and a failure`() = runBlocking {
        val f = Fixture()
        f.diagnostics.setScenario(SimulationScenario.DEGRADED)
        val run = f.diagnostics.runSensors()
        assertEquals(DiagnosticState.FAILED, run.worst)
        assertTrue(run.warnings.size >= 2)
        assertEquals(1, run.failed.size)
        assertEquals(DiagnosticState.WARNING, f.diagnostics.runReadiness().worst)
    }

    @Test
    fun `latest sensor run and scenario persist`() = runBlocking {
        val f1 = Fixture()
        f1.diagnostics.setScenario(SimulationScenario.DEGRADED)
        f1.diagnostics.runSensors()
        val f2 = Fixture(store = f1.store)
        assertEquals(SimulationScenario.DEGRADED, f2.diagnostics.scenario.value)
        assertEquals(DiagnosticState.FAILED, f2.diagnostics.latestSensors.value?.worst)
    }
}

class WizardAndValidationTest {

    private val ground = "floor-ground"

    @Test
    fun `nothing is complete before an installation starts`() {
        val f = Fixture()
        val statuses = WizardLogic.statuses(null, f.floors.floors.value, emptyList())
        assertEquals(7, statuses.size)
        assertTrue(statuses.none { it.isComplete })
        assertTrue(statuses.all { it.blockedReason != null })
        assertEquals(0, WizardLogic.firstIncomplete(statuses))
    }

    @Test
    fun `steps complete in order and gate navigation`() = runBlocking {
        val f = Fixture()
        f.installations.startNew()
        fun statuses() = WizardLogic.statuses(f.installations.current.value, f.floors.floors.value, f.mapping.maps.value)

        assertFalse(statuses()[0].isComplete)
        assertTrue(WizardLogic.canEnter(WizardStep.OVERVIEW, statuses()))
        assertFalse(WizardLogic.canEnter(WizardStep.READINESS, statuses()))

        f.installations.acknowledgeOverview()
        assertTrue(WizardLogic.canEnter(WizardStep.READINESS, statuses()))
        assertFalse(WizardLogic.canEnter(WizardStep.SENSORS, statuses()))

        f.installations.recordReadiness(f.diagnostics.runReadiness())
        f.installations.recordSensors(f.diagnostics.runSensors())
        assertEquals(StepState.COMPLETE, statuses()[1].state)
        assertEquals(StepState.COMPLETE, statuses()[2].state)

        f.installations.setSelectedFloors(listOf(ground, "floor-first"))
        assertTrue(statuses()[3].isComplete)
        assertFalse(statuses()[4].isComplete)
        assertTrue(statuses()[4].blockedReason!!.contains("Ground floor"))

        f.mapFloor(ground, "G")
        f.mapFloor("floor-first", "F")
        assertTrue(statuses()[4].isComplete)

        assertFalse(statuses()[5].isComplete)
        f.installations.markValidationRun()
        assertTrue(statuses()[5].isComplete) // only warnings (pending checklist), no errors
        assertEquals(StepState.COMPLETE_WITH_WARNINGS, statuses()[5].state)

        f.services.generateReport()
        assertTrue(statuses().all { it.isComplete })
        assertTrue(WizardLogic.canEnter(WizardStep.REPORT, statuses()))
    }

    @Test
    fun `degraded diagnostics complete the step with warnings and block validation`() = runBlocking {
        val f = Fixture()
        f.diagnostics.setScenario(SimulationScenario.DEGRADED)
        f.installations.startNew()
        f.installations.recordSensors(f.diagnostics.runSensors())
        val inst = f.installations.current.value
        val sensors = WizardLogic.statuses(inst, f.floors.floors.value, emptyList())[2]
        assertEquals(StepState.COMPLETE_WITH_WARNINGS, sensors.state)
        assertTrue(sensors.warnings.any { it.contains("Mapping service") })
        val validation = ValidationLogic.validate(inst, f.floors.floors.value, emptyList())
        assertFalse(validation.passed)
        assertTrue(validation.errors.any { it.message.contains("Mapping service failed") })
    }

    @Test
    fun `validation errors warnings and the checklist rules`() {
        val f = Fixture()
        f.installations.startNew()
        var v = ValidationLogic.validate(f.installations.current.value, f.floors.floors.value, emptyList())
        assertTrue(v.errors.any { it.message.contains("Readiness") })
        assertTrue(v.errors.any { it.message.contains("No floors are selected") })

        // Required pending items are warnings, a required blocked item is an error.
        assertTrue(v.warnings.any { it.message.contains("Site walkthrough") })
        f.installations.updateChecklist("site-walk", ChecklistState.BLOCKED, "No access yet")
        v = ValidationLogic.validate(f.installations.current.value, f.floors.floors.value, emptyList())
        assertTrue(v.errors.any { it.message.contains("Checklist item blocked: Site walkthrough") })
        f.installations.updateChecklist("site-walk", ChecklistState.DONE, "")
        v = ValidationLogic.validate(f.installations.current.value, f.floors.floors.value, emptyList())
        assertFalse(v.errors.any { it.message.contains("Site walkthrough") })
    }

    @Test
    fun `a partial map is only a warning and a deleted floor is an error`() {
        val f = Fixture()
        f.installations.startNew()
        f.installations.setSelectedFloors(listOf(ground))
        f.mapFloor(ground, "Half", fullCoverage = false)
        var v = ValidationLogic.validate(f.installations.current.value, f.floors.floors.value, f.mapping.maps.value)
        assertTrue(v.warnings.any { it.message.contains("partial-coverage") })
        assertFalse(v.errors.any { it.message.contains("no saved simulated map") })

        f.mapping.deleteMap(f.mapping.maps.value.single().id)
        f.floors.deleteFloor(ground)
        v = ValidationLogic.validate(f.installations.current.value, f.floors.floors.value, f.mapping.maps.value)
        assertTrue(v.errors.any { it.message.contains("no longer exists") })
    }

    @Test
    fun `checklist states require notes where they explain a deviation`() {
        val f = Fixture()
        f.installations.startNew()
        assertEquals(
            ErrorCode.NOTE_REQUIRED,
            f.installations.updateChecklist("wifi", ChecklistState.BLOCKED, "  ").failureOrNull()?.code
        )
        assertEquals(
            ErrorCode.NOTE_REQUIRED,
            f.installations.updateChecklist("wifi", ChecklistState.NOT_APPLICABLE, "").failureOrNull()?.code
        )
        assertTrue(f.installations.updateChecklist("wifi", ChecklistState.NOT_APPLICABLE, "Wired site").isSuccess)
        assertEquals(ErrorCode.NOT_FOUND, f.installations.updateChecklist("nope", ChecklistState.DONE, "").failureOrNull()?.code)
        assertEquals(
            ErrorCode.INVALID_NAME,
            f.installations.updateChecklist("wifi", ChecklistState.DONE, "x".repeat(201)).failureOrNull()?.code
        )
        val entry = f.installations.current.value!!.checklist.first { it.id == "wifi" }
        assertEquals(ChecklistState.NOT_APPLICABLE, entry.state)
        assertEquals("Wired site", entry.note)
    }

    @Test
    fun `checklist edits without an installation fail clearly`() {
        val f = Fixture()
        assertEquals(
            ErrorCode.NO_INSTALLATION,
            f.installations.updateChecklist("wifi", ChecklistState.DONE, "").failureOrNull()?.code
        )
    }

    @Test
    fun `installation state persists and a new installation resets progress`() {
        val f1 = Fixture()
        val first = f1.installations.startNew()
        f1.installations.acknowledgeOverview()
        f1.installations.setSelectedFloors(listOf(ground))
        val f2 = Fixture(store = f1.store)
        assertEquals(first.id, f2.installations.current.value?.id)
        assertTrue(f2.installations.current.value!!.overviewAcknowledged)

        // (Ids come from a per-fixture sequence here, so only the reset behaviour is asserted.)
        val second = f2.installations.startNew()
        assertFalse(second.overviewAcknowledged)
        assertTrue(second.selectedFloorIds.isEmpty())
        assertEquals(8, second.checklist.size)
    }
}

class ReportTest {

    @Test
    fun `report contains installation id floors status checklist warnings timestamp and the simulation notice`() {
        val f = Fixture()
        f.installations.startNew()
        f.installations.setSelectedFloors(listOf("floor-ground"))
        f.mapFloor("floor-ground", "Hall")
        f.installations.updateChecklist("site-walk", ChecklistState.DONE, "Walked all areas")

        val installation = f.installations.current.value!!
        val report = f.services.generateReport()

        assertEquals(installation.id, report.installationId)
        assertEquals(listOf("Ground floor"), report.floors.map { it.name })
        assertEquals("Hall", report.floors.single().maps.single().name)
        assertTrue(report.mappingStatus.contains("1 of 1"))
        assertTrue(report.mappingStatus.contains("Simulated"))
        assertEquals(f.clock.now, report.generatedAtMillis)
        assertTrue(report.warnings.isNotEmpty(), "outstanding warnings are listed")
        assertTrue(report.warnings.any { it.startsWith("BLOCKING:") }, "not-run diagnostics block")
        assertEquals(SimulationNotice.FULL, report.simulationNotice)
        assertEquals(report.id, f.installations.current.value?.reportId)

        val text = f.reports.render(report)
        assertTrue(text.startsWith("*** SIMULATION ONLY ***"))
        assertTrue(text.contains("Installation ID: ${installation.id}"))
        assertTrue(text.contains("Walked all areas"))
        assertTrue(text.contains("No physical robot, sensor, map or network was accessed"))
        assertTrue(text.contains("Generated: 2023-11-14"))
    }

    @Test
    fun `reports persist newest first and can be deleted`() {
        val f1 = Fixture()
        f1.installations.startNew()
        val a = f1.services.generateReport()
        val b = f1.services.generateReport()
        val f2 = Fixture(store = f1.store)
        assertEquals(listOf(b.id, a.id), f2.reports.reports.value.map { it.id })
        assertTrue(f2.reports.delete(a.id).isSuccess)
        assertEquals(1, f2.reports.reports.value.size)
        assertEquals(ErrorCode.NOT_FOUND, f2.reports.delete(a.id).failureOrNull()?.code)
    }

    @Test
    fun `report with no floors states that clearly`() {
        val f = Fixture()
        val installation = f.installations.startNew()
        val report = ReportBuilder.build("RPT-X", 0L, installation, f.floors.floors.value, emptyList())
        assertEquals("Simulated mapping: no floors selected.", report.mappingStatus)
        assertNull(f.installations.current.value?.reportId)
        assertNotNull(report.checklist.firstOrNull())
    }
}
