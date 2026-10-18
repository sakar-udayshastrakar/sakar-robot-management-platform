package com.sakarrobotics.installation.core.model

/**
 * Where the data shown by the app comes from. Phase 1 ships exactly one backend: [SIMULATED].
 * There is intentionally no REAL value - a real backend may only be added after it has been
 * authorised and implemented (see [com.sakarrobotics.installation.core.hardware.RobotHardwarePort]).
 */
enum class BackendMode { SIMULATED }

data class BackendInfo(
    val mode: BackendMode,
    val label: String,
    val description: String
)

data class Floor(val id: String, val name: String, val level: Int)

// ---------------------------------------------------------------- maps

enum class CellState(val symbol: Char) {
    UNKNOWN('?'),
    FREE('.'),
    WALL('#');

    companion object {
        fun fromSymbol(c: Char): CellState = values().firstOrNull { it.symbol == c } ?: UNKNOWN
    }
}

/**
 * Small text-encoded occupancy grid used for the simulated map canvas and previews. One string per
 * row, one character per cell (see [CellState.symbol]). This is a Sakar demo format - it is NOT the
 * vendor map file format and makes no claim of compatibility with it.
 */
data class OccupancyGrid(val rows: List<String>) {
    val height: Int get() = rows.size
    val width: Int get() = rows.firstOrNull()?.length ?: 0

    operator fun get(x: Int, y: Int): CellState =
        rows.getOrNull(y)?.getOrNull(x)?.let(CellState::fromSymbol) ?: CellState.UNKNOWN

    fun count(state: CellState): Int = rows.sumOf { row -> row.count { it == state.symbol } }

    companion object {
        fun unknown(width: Int, height: Int) = OccupancyGrid(List(height) { "?".repeat(width) })
    }
}

enum class MapStatus(val label: String) {
    /** Simulated coverage reached the "complete" threshold. */
    COMPLETE("Simulated - complete"),

    /** Saved with less coverage than the threshold; flagged for review. */
    PARTIAL("Simulated - partial coverage")
}

data class SimMap(
    val id: String,
    val name: String,
    val floorId: String,
    val createdAtMillis: Long,
    val status: MapStatus,
    val coveragePercent: Int,
    val preview: OccupancyGrid
) {
    /** Always true: every map in this release is simulated demo data. */
    val simulated: Boolean get() = true
}

enum class MappingPhase { IDLE, MAPPING, PAUSED, FINISHED, CANCELLED }

data class MappingSessionState(
    val phase: MappingPhase = MappingPhase.IDLE,
    val floorId: String? = null,
    val progressPercent: Int = 0,
    val grid: OccupancyGrid? = null,
    val robotX: Int = -1,
    val robotY: Int = -1,
    val message: String? = null
) {
    val isActive: Boolean get() = phase == MappingPhase.MAPPING || phase == MappingPhase.PAUSED
}

// ---------------------------------------------------------------- diagnostics

enum class DiagnosticCategory { READINESS, SENSOR }

enum class DiagnosticState(val label: String) {
    NOT_RUN("Not run"),
    OK("OK"),
    WARNING("Warning"),
    FAILED("Failed")
}

enum class SimulationScenario(val label: String, val description: String) {
    HEALTHY("Healthy demo", "All simulated checks pass."),
    DEGRADED("Degraded demo", "Shows example warnings and a failure so the guidance can be reviewed.")
}

data class DiagnosticItem(
    val id: String,
    val category: DiagnosticCategory,
    val title: String,
    val state: DiagnosticState,
    val message: String,
    val troubleshooting: List<String>
) {
    /** Always true: no physical sensor or service is ever checked by this release. */
    val simulated: Boolean get() = true
}

data class DiagnosticsRun(
    val runAtMillis: Long,
    val scenario: SimulationScenario,
    val items: List<DiagnosticItem>
) {
    val failed: List<DiagnosticItem> get() = items.filter { it.state == DiagnosticState.FAILED }
    val warnings: List<DiagnosticItem> get() = items.filter { it.state == DiagnosticState.WARNING }
    val worst: DiagnosticState
        get() = when {
            items.isEmpty() -> DiagnosticState.NOT_RUN
            failed.isNotEmpty() -> DiagnosticState.FAILED
            warnings.isNotEmpty() -> DiagnosticState.WARNING
            else -> DiagnosticState.OK
        }
}

// ---------------------------------------------------------------- checklist

enum class ChecklistState(val label: String) {
    PENDING("Pending"),
    DONE("Done"),
    NOT_APPLICABLE("Not applicable"),
    BLOCKED("Blocked")
}

data class ChecklistEntry(
    val id: String,
    val title: String,
    val description: String,
    val required: Boolean,
    val state: ChecklistState = ChecklistState.PENDING,
    val note: String = ""
)

// ---------------------------------------------------------------- installation / wizard

enum class WizardStep(val index: Int, val title: String, val summary: String) {
    OVERVIEW(0, "Installation overview", "What this simulated installation covers."),
    READINESS(1, "System readiness", "Simulated pre-installation readiness checks."),
    SENSORS(2, "Sensor diagnostics", "Simulated sensor and service diagnostics."),
    MAPPING_SETUP(3, "Mapping setup", "Choose the floors to map."),
    MAP_CONFIG(4, "Map and floor configuration", "Create and save a simulated map for every selected floor."),
    VALIDATION(5, "Installation validation", "Check the installation for errors and warnings."),
    REPORT(6, "Completion report", "Generate the local installation report.")
}

data class Installation(
    val id: String,
    val createdAtMillis: Long,
    val overviewAcknowledged: Boolean = false,
    val readiness: DiagnosticsRun? = null,
    val sensors: DiagnosticsRun? = null,
    val selectedFloorIds: List<String> = emptyList(),
    val validationRunAtMillis: Long? = null,
    val reportId: String? = null,
    val checklist: List<ChecklistEntry> = emptyList()
)

enum class StepState { NOT_STARTED, COMPLETE, COMPLETE_WITH_WARNINGS }

data class StepStatus(
    val step: WizardStep,
    val state: StepState,
    val warnings: List<String>,
    /** Why the step is not complete yet (null when complete). Shown instead of a dead button. */
    val blockedReason: String?
) {
    val isComplete: Boolean get() = state != StepState.NOT_STARTED
}

data class ValidationIssue(val message: String, val step: WizardStep?)

data class ValidationResult(
    val errors: List<ValidationIssue>,
    val warnings: List<ValidationIssue>
) {
    val passed: Boolean get() = errors.isEmpty()
}

// ---------------------------------------------------------------- report

data class ReportMap(val name: String, val status: String, val coveragePercent: Int)

data class ReportFloor(val name: String, val maps: List<ReportMap>)

data class ReportChecklistItem(
    val title: String,
    val required: Boolean,
    val state: String,
    val note: String
)

data class InstallationReport(
    val id: String,
    val installationId: String,
    val generatedAtMillis: Long,
    val floors: List<ReportFloor>,
    val mappingStatus: String,
    val diagnosticsSummary: String,
    val checklist: List<ReportChecklistItem>,
    val warnings: List<String>,
    val simulationNotice: String,
    /** Added in 0.2.0; null on reports saved by 0.1.0. */
    val completionStatus: String? = null
)
