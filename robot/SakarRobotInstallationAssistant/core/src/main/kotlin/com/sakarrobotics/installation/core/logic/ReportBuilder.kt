package com.sakarrobotics.installation.core.logic

import com.sakarrobotics.installation.core.model.ChecklistState
import com.sakarrobotics.installation.core.model.DiagnosticsRun
import com.sakarrobotics.installation.core.model.Floor
import com.sakarrobotics.installation.core.model.Installation
import com.sakarrobotics.installation.core.model.InstallationReport
import com.sakarrobotics.installation.core.model.ReportChecklistItem
import com.sakarrobotics.installation.core.model.ReportFloor
import com.sakarrobotics.installation.core.model.ReportMap
import com.sakarrobotics.installation.core.model.SimMap
import com.sakarrobotics.installation.core.model.WizardStep
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object SimulationNotice {
    const val SHORT = "SIMULATION ONLY"

    /** Prominent banner printed at the top of every exported PDF page. */
    const val PDF_BANNER = "SIMULATION ONLY — NOT VALIDATED ON PHYSICAL ROBOT"
    const val FULL = "SIMULATION ONLY - this report was generated from simulated demo data. No physical " +
        "robot, sensor, map or network was accessed, and it is not evidence of a real installation."
}

/** Builds and renders the local installation report. It contains no customer or personal data. */
object ReportBuilder {

    fun build(
        reportId: String,
        now: Long,
        installation: Installation,
        floors: List<Floor>,
        maps: List<SimMap>
    ): InstallationReport {
        val selected = installation.selectedFloorIds.mapNotNull { id -> floors.firstOrNull { it.id == id } }
        val reportFloors = selected.map { floor ->
            ReportFloor(
                floor.name,
                maps.filter { it.floorId == floor.id }
                    .map { ReportMap(it.name, it.status.label, it.coveragePercent) }
            )
        }
        val validation = ValidationLogic.validate(installation, floors, maps)
        val warnings = validation.errors.map { "BLOCKING: ${it.message}" } +
            validation.warnings.map { "WARNING: ${it.message}" }

        return InstallationReport(
            id = reportId,
            installationId = installation.id,
            generatedAtMillis = now,
            floors = reportFloors,
            mappingStatus = mappingStatus(reportFloors),
            diagnosticsSummary = diagnosticsSummary(installation.readiness, installation.sensors),
            checklist = installation.checklist.map {
                ReportChecklistItem(it.title, it.required, it.state.label, it.note)
            },
            warnings = warnings,
            simulationNotice = SimulationNotice.FULL,
            completionStatus = completionStatus(installation, floors, maps)
        )
    }

    /** Workflow progress at the time of the report (the report step itself is excluded - it is being created). */
    fun completionStatus(installation: Installation, floors: List<Floor>, maps: List<SimMap>): String {
        val steps = WizardLogic.statuses(installation, floors, maps).filter { it.step != WizardStep.REPORT }
        val done = steps.count { it.isComplete }
        val resolved = installation.checklist.count {
            it.state == ChecklistState.DONE || it.state == ChecklistState.NOT_APPLICABLE
        }
        val label = if (done == steps.size) "Workflow complete (simulated)" else "Workflow incomplete (simulated)"
        return "$label - $done of ${steps.size} steps complete; checklist $resolved of ${installation.checklist.size} resolved."
    }

    private fun mappingStatus(floors: List<ReportFloor>): String {
        if (floors.isEmpty()) return "Simulated mapping: no floors selected."
        val mapped = floors.count { it.maps.isNotEmpty() }
        return "Simulated mapping: $mapped of ${floors.size} selected floor(s) have a saved simulated map."
    }

    private fun diagnosticsSummary(readiness: DiagnosticsRun?, sensors: DiagnosticsRun?): String {
        fun part(label: String, run: DiagnosticsRun?) =
            if (run == null) "$label (simulated): not run"
            else "$label (simulated): ${run.worst.label} - ${run.warnings.size} warning(s), ${run.failed.size} failure(s)"
        return part("Readiness", readiness) + "; " + part("Sensors", sensors)
    }

    fun render(report: InstallationReport): String = buildString {
        appendLine("*** ${SimulationNotice.SHORT} ***")
        appendLine("Sakar Robotics - Robot Installation Assistant")
        appendLine("Installation report")
        appendLine()
        appendLine("Installation ID: ${report.installationId}")
        appendLine("Report ID: ${report.id}")
        appendLine("Generated: ${formatTimestamp(report.generatedAtMillis)}")
        appendLine("Completion: ${report.completionStatus ?: NOT_RECORDED}")
        appendLine()
        appendLine("Selected floors")
        if (report.floors.isEmpty()) appendLine("  (none)")
        report.floors.forEach { floor ->
            appendLine("  - ${floor.name}")
            if (floor.maps.isEmpty()) appendLine("      no saved simulated map")
            floor.maps.forEach { appendLine("      map \"${it.name}\": ${it.status}, ${it.coveragePercent}% coverage") }
        }
        appendLine()
        appendLine(report.mappingStatus)
        appendLine(report.diagnosticsSummary)
        appendLine()
        appendLine("Checklist")
        if (report.checklist.isEmpty()) appendLine("  (empty)")
        report.checklist.forEach {
            val req = if (it.required) "required" else "optional"
            val note = if (it.note.isBlank()) "" else " - ${it.note}"
            appendLine("  [${it.state}] ${it.title} ($req)$note")
        }
        appendLine()
        appendLine("Outstanding warnings")
        if (report.warnings.isEmpty()) appendLine("  none")
        report.warnings.forEach { appendLine("  - $it") }
        appendLine()
        appendLine(report.simulationNotice)
    }

    const val NOT_RECORDED = "not recorded (report created by an earlier version)"

    fun formatTimestamp(millis: Long): String {
        val format = SimpleDateFormat("yyyy-MM-dd HH:mm 'UTC'", Locale.US)
        format.timeZone = TimeZone.getTimeZone("UTC")
        return format.format(Date(millis))
    }
}

/** The default installation checklist (generic site-readiness items, no vendor text). */
object DefaultChecklist {
    fun entries(): List<com.sakarrobotics.installation.core.model.ChecklistEntry> = listOf(
        entry("site-walk", "Site walkthrough completed", "All areas the robot must serve have been walked.", true),
        entry("charger", "Charging position planned", "A suitable charger location has been identified.", true),
        entry("restricted", "Restricted areas identified", "Stairs, glass walls and no-go zones are noted.", true),
        entry("access", "Doors, gates and lifts confirmed", "Access for the robot has been confirmed.", false),
        entry("wifi", "Network coverage checked", "Coverage along the route has been reviewed.", false),
        entry("map-review", "Map reviewed and approved", "The saved map has been reviewed with the site owner.", true),
        entry("operator", "Operator briefed", "The on-site operator knows basic operation and safety.", true),
        entry("safety", "Safety review done", "Safety notes have been shared and acknowledged.", true)
    )

    private fun entry(id: String, title: String, description: String, required: Boolean) =
        com.sakarrobotics.installation.core.model.ChecklistEntry(id, title, description, required)
}
