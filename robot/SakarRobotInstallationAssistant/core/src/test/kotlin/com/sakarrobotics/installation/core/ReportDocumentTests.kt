package com.sakarrobotics.installation.core

import com.sakarrobotics.installation.core.logic.LineStyle
import com.sakarrobotics.installation.core.logic.ReportBuilder
import com.sakarrobotics.installation.core.logic.ReportDocumentBuilder
import com.sakarrobotics.installation.core.logic.SimulationNotice
import com.sakarrobotics.installation.core.model.ChecklistState
import com.sakarrobotics.installation.core.model.InstallationReport
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReportDocumentTest {

    private val ground = "floor-ground"

    private fun populated(): Fixture {
        val f = Fixture()
        f.installations.startNew()
        f.installations.acknowledgeOverview()
        f.installations.setSelectedFloors(listOf(ground))
        f.mapFloor(ground, "Lobby sweep")
        f.installations.updateChecklist("site-walk", ChecklistState.DONE, "")
        f.installations.updateChecklist("wifi", ChecklistState.BLOCKED, "Dead spot near the lift")
        return f
    }

    @Test
    fun `document carries the prominent simulation banner and every required report fact`() {
        val f = populated()
        val report = f.services.generateReport()
        val doc = ReportDocumentBuilder.build(report)
        val text = doc.allText()

        assertEquals("SIMULATION ONLY — NOT VALIDATED ON PHYSICAL ROBOT", doc.banner)
        assertEquals(SimulationNotice.PDF_BANNER, doc.banner)
        assertEquals(doc.banner, text.first(), "the banner is the first thing on the page")

        assertTrue(text.any { it == "Installation ID: ${report.installationId}" })
        assertTrue(text.any { it == "Report ID: ${report.id}" })
        assertTrue(text.any { it.startsWith("Generated: ") && it.endsWith("UTC") }, "date and time")
        assertTrue(text.any { it.startsWith("Completion status: Workflow") }, "completion status")
        assertTrue(text.any { it.contains("Ground floor") }, "floor information")
        assertTrue(text.any { it.contains("Lobby sweep") && it.contains("simulated coverage") }, "map status")
        assertTrue(text.any { it.startsWith("Simulated mapping:") }, "mapping status")
        assertTrue(text.any { it.contains("Site walkthrough completed") && it.startsWith("[Done]") })
        assertTrue(text.any { it.contains("Network coverage checked") && it.contains("Dead spot near the lift") })
        assertTrue(text.any { it.startsWith("- WARNING: ") || it.startsWith("- BLOCKING: ") }, "outstanding warnings")
        assertEquals(report.simulationNotice, doc.footerNotice)
        assertTrue(doc.footerNotice.startsWith("SIMULATION ONLY"))
    }

    @Test
    fun `document states that diagnostics and maps are not real`() {
        val doc = ReportDocumentBuilder.build(populated().services.generateReport())
        val text = doc.allText()
        assertTrue(text.any { it.contains("No physical sensor was read") })
        assertTrue(text.any { it.contains("not in any vendor map format") })
    }

    @Test
    fun `required checklist items and warnings get distinct styles`() {
        val doc = ReportDocumentBuilder.build(populated().services.generateReport())
        val checklist = doc.sections.single { it.title == "Checklist results" }.lines
        assertTrue(checklist.any { it.style == LineStyle.ITEM_REQUIRED })
        assertTrue(checklist.any { it.style == LineStyle.ITEM_OPTIONAL })
        assertTrue(doc.sections.single { it.title == "Outstanding warnings" }.lines.all { it.style == LineStyle.WARNING })
    }

    @Test
    fun `empty installation still produces a valid honest document`() {
        val f = Fixture()
        f.installations.startNew()
        val doc = ReportDocumentBuilder.build(f.services.generateReport())
        val text = doc.allText()
        assertTrue(text.any { it == "No floors were selected." })
        assertTrue(text.any { it.contains("Workflow incomplete (simulated)") })
        assertTrue(text.contains(SimulationNotice.PDF_BANNER))
    }

    @Test
    fun `report saved by the previous version renders without a completion status`() {
        val old = ReportDocumentBuilder.build(
            InstallationReport(
                id = "RPT-OLD", installationId = "INST-OLD", generatedAtMillis = 1_700_000_000_000L,
                floors = emptyList(), mappingStatus = "Simulated mapping: no floors selected.",
                diagnosticsSummary = "x", checklist = emptyList(), warnings = emptyList(),
                simulationNotice = SimulationNotice.FULL, completionStatus = null
            )
        )
        assertTrue(old.allText().any { it.contains(ReportBuilder.NOT_RECORDED) })
    }

    @Test
    fun `document and text report contain no device or account identifiers`() {
        val f = populated()
        val report = f.services.generateReport()
        val everything = (ReportDocumentBuilder.build(report).allText() + f.reports.render(report)).joinToString("\n").lowercase()
        listOf("android_id", "serial", "imei", "mac address", "password", "token", "secret", "@", "http", "192.168", "ssid")
            .forEach { assertFalse(everything.contains(it), "report must not contain '$it'") }
    }

    @Test
    fun `completion status reflects progress`() {
        val f = Fixture()
        val inst = f.installations.startNew()
        val start = ReportBuilder.completionStatus(inst, f.floors.floors.value, f.mapping.maps.value)
        assertTrue(start.startsWith("Workflow incomplete (simulated) - 0 of 6 steps"), start)
    }
}
