package com.sakarrobotics.installation.core.logic

import com.sakarrobotics.installation.core.model.ChecklistState
import com.sakarrobotics.installation.core.model.DiagnosticState
import com.sakarrobotics.installation.core.model.DiagnosticsRun
import com.sakarrobotics.installation.core.model.Floor
import com.sakarrobotics.installation.core.model.Installation
import com.sakarrobotics.installation.core.model.MapStatus
import com.sakarrobotics.installation.core.model.SimMap
import com.sakarrobotics.installation.core.model.StepState
import com.sakarrobotics.installation.core.model.StepStatus
import com.sakarrobotics.installation.core.model.ValidationIssue
import com.sakarrobotics.installation.core.model.ValidationResult
import com.sakarrobotics.installation.core.model.WizardStep

/** Pure validation rules for a (simulated) installation. */
object ValidationLogic {

    fun validate(installation: Installation?, floors: List<Floor>, maps: List<SimMap>): ValidationResult {
        val errors = mutableListOf<ValidationIssue>()
        val warnings = mutableListOf<ValidationIssue>()
        if (installation == null) {
            errors += ValidationIssue("No installation has been started.", WizardStep.OVERVIEW)
            return ValidationResult(errors, warnings)
        }

        checkRun("Readiness", installation.readiness, WizardStep.READINESS, errors, warnings)
        checkRun("Sensor diagnostics", installation.sensors, WizardStep.SENSORS, errors, warnings)

        val knownFloors = floors.associateBy { it.id }
        if (installation.selectedFloorIds.isEmpty()) {
            errors += ValidationIssue("No floors are selected for mapping.", WizardStep.MAPPING_SETUP)
        }
        for (floorId in installation.selectedFloorIds) {
            val floor = knownFloors[floorId]
            if (floor == null) {
                errors += ValidationIssue("A selected floor no longer exists.", WizardStep.MAPPING_SETUP)
                continue
            }
            val floorMaps = maps.filter { it.floorId == floorId }
            if (floorMaps.isEmpty()) {
                errors += ValidationIssue("\"${floor.name}\" has no saved simulated map.", WizardStep.MAP_CONFIG)
            } else if (floorMaps.none { it.status == MapStatus.COMPLETE }) {
                warnings += ValidationIssue(
                    "\"${floor.name}\" only has partial-coverage simulated maps.", WizardStep.MAP_CONFIG
                )
            }
        }

        for (entry in installation.checklist) {
            when {
                entry.required && entry.state == ChecklistState.BLOCKED ->
                    errors += ValidationIssue("Checklist item blocked: ${entry.title}.", null)
                entry.required && entry.state == ChecklistState.PENDING ->
                    warnings += ValidationIssue("Checklist item still pending: ${entry.title}.", null)
                !entry.required && entry.state == ChecklistState.BLOCKED ->
                    warnings += ValidationIssue("Optional checklist item blocked: ${entry.title}.", null)
            }
        }
        return ValidationResult(errors, warnings)
    }

    private fun checkRun(
        label: String,
        run: DiagnosticsRun?,
        step: WizardStep,
        errors: MutableList<ValidationIssue>,
        warnings: MutableList<ValidationIssue>
    ) {
        if (run == null) {
            errors += ValidationIssue("$label (simulated) has not been run.", step)
            return
        }
        run.failed.forEach { errors += ValidationIssue("$label (simulated): ${it.title} failed.", step) }
        run.warnings.forEach { warnings += ValidationIssue("$label (simulated): ${it.title} - warning.", step) }
    }
}

/** Pure step-completion and navigation rules for the installation wizard. */
object WizardLogic {

    fun statuses(installation: Installation?, floors: List<Floor>, maps: List<SimMap>): List<StepStatus> {
        val validation = ValidationLogic.validate(installation, floors, maps)
        return WizardStep.values().map { step -> status(step, installation, floors, maps, validation) }
    }

    private fun status(
        step: WizardStep,
        inst: Installation?,
        floors: List<Floor>,
        maps: List<SimMap>,
        validation: ValidationResult
    ): StepStatus {
        fun notStarted(reason: String) = StepStatus(step, StepState.NOT_STARTED, emptyList(), reason)
        fun done(warnings: List<String> = emptyList()) = StepStatus(
            step,
            if (warnings.isEmpty()) StepState.COMPLETE else StepState.COMPLETE_WITH_WARNINGS,
            warnings,
            null
        )
        if (inst == null) return notStarted("Start a new installation first.")

        return when (step) {
            WizardStep.OVERVIEW ->
                if (inst.overviewAcknowledged) done() else notStarted("Confirm that you have read the overview.")

            WizardStep.READINESS -> runStatus(step, inst.readiness, "readiness checks")
            WizardStep.SENSORS -> runStatus(step, inst.sensors, "sensor diagnostics")

            WizardStep.MAPPING_SETUP -> {
                val valid = inst.selectedFloorIds.filter { id -> floors.any { it.id == id } }
                if (valid.isEmpty()) notStarted("Select at least one floor to map.") else done()
            }

            WizardStep.MAP_CONFIG -> {
                val selected = inst.selectedFloorIds.mapNotNull { id -> floors.firstOrNull { it.id == id } }
                if (selected.isEmpty()) {
                    notStarted("Select floors in the previous step first.")
                } else {
                    val missing = selected.filter { f -> maps.none { it.floorId == f.id } }
                    if (missing.isNotEmpty()) {
                        notStarted("Save a simulated map for: ${missing.joinToString { it.name }}.")
                    } else {
                        done(validation.warnings.filter { it.step == WizardStep.MAP_CONFIG }.map { it.message })
                    }
                }
            }

            WizardStep.VALIDATION -> when {
                inst.validationRunAtMillis == null -> notStarted("Run the validation.")
                !validation.passed ->
                    notStarted("Fix ${validation.errors.size} error(s) and run the validation again.")
                else -> done(validation.warnings.map { it.message })
            }

            WizardStep.REPORT ->
                if (inst.reportId != null) done() else notStarted("Generate the report.")
        }
    }

    private fun runStatus(step: WizardStep, run: DiagnosticsRun?, what: String): StepStatus =
        if (run == null) {
            StepStatus(step, StepState.NOT_STARTED, emptyList(), "Run the simulated $what.")
        } else {
            val warnings = run.items
                .filter { it.state == DiagnosticState.WARNING || it.state == DiagnosticState.FAILED }
                .map { "${it.title}: ${it.state.label.lowercase()} (simulated)" }
            StepStatus(
                step,
                if (warnings.isEmpty()) StepState.COMPLETE else StepState.COMPLETE_WITH_WARNINGS,
                warnings,
                null
            )
        }

    /** Index of the first step that is not complete, or the last step if everything is complete. */
    fun firstIncomplete(statuses: List<StepStatus>): Int =
        statuses.indexOfFirst { !it.isComplete }.let { if (it == -1) statuses.lastIndex else it }

    /** A step can be opened if every step before it is complete (the furthest reachable step is the first incomplete one). */
    fun canEnter(step: WizardStep, statuses: List<StepStatus>): Boolean =
        step.index <= firstIncomplete(statuses)

    fun nextStep(step: WizardStep): WizardStep? = WizardStep.values().getOrNull(step.index + 1)
    fun previousStep(step: WizardStep): WizardStep? = WizardStep.values().getOrNull(step.index - 1)
}
