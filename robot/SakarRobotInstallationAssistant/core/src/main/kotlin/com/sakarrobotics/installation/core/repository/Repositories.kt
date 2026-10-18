package com.sakarrobotics.installation.core.repository

import com.sakarrobotics.installation.core.model.BackendInfo
import com.sakarrobotics.installation.core.model.ChecklistState
import com.sakarrobotics.installation.core.model.DiagnosticsRun
import com.sakarrobotics.installation.core.model.Floor
import com.sakarrobotics.installation.core.model.Installation
import com.sakarrobotics.installation.core.model.InstallationReport
import com.sakarrobotics.installation.core.model.MappingSessionState
import com.sakarrobotics.installation.core.model.Outcome
import com.sakarrobotics.installation.core.model.SimMap
import com.sakarrobotics.installation.core.model.SimulationScenario
import kotlinx.coroutines.flow.StateFlow

/** Floors known to this installation. Local data only. */
interface FloorRepository {
    val floors: StateFlow<List<Floor>>
    fun addFloor(name: String): Outcome<Floor>
    fun renameFloor(id: String, name: String): Outcome<Floor>

    /** Refused while the floor still has saved maps. */
    fun deleteFloor(id: String): Outcome<Unit>
}

/**
 * Mapping session control plus the library of saved maps.
 *
 * The only implementation in this release is simulated. A future authorised backend would implement
 * this same interface; the UI must not need to change.
 */
interface MappingRepository {
    val backend: BackendInfo
    val session: StateFlow<MappingSessionState>
    val maps: StateFlow<List<SimMap>>

    /** The saved map currently opened for preview in the workspace, if any. */
    val loadedMap: StateFlow<SimMap?>

    fun startSession(floorId: String): Outcome<Unit>
    fun pause(): Outcome<Unit>
    fun resume(): Outcome<Unit>

    /** Ends scanning. Refused if too little has been scanned. */
    fun finish(): Outcome<Unit>
    fun cancel(): Outcome<Unit>

    /** Saves the finished session as a named map on the session floor. */
    fun saveMap(name: String): Outcome<SimMap>
    fun loadMap(id: String): Outcome<SimMap>
    fun closeLoadedMap()
    fun renameMap(id: String, name: String): Outcome<SimMap>
    fun deleteMap(id: String): Outcome<Unit>
    fun resetSession()

    /**
     * Pauses a running session and stops its timer (a paused session keeps its progress). Called when the
     * workspace is left or the app goes to the background, so no simulation keeps running unseen.
     */
    fun interrupt(reason: String)

    /** Stops any timer. The session state is kept. */
    fun shutdown()
}

interface DiagnosticsRepository {
    val backend: BackendInfo
    val scenario: StateFlow<SimulationScenario>
    fun setScenario(scenario: SimulationScenario)

    /** Most recent sensor diagnostics run (any installation), if any. */
    val latestSensors: StateFlow<DiagnosticsRun?>

    suspend fun runReadiness(): DiagnosticsRun
    suspend fun runSensors(): DiagnosticsRun
}

interface InstallationRepository {
    val current: StateFlow<Installation?>

    fun startNew(): Installation

    /** Returns the current installation, creating one if none exists. */
    fun ensureCurrent(): Installation

    fun acknowledgeOverview()
    fun recordReadiness(run: DiagnosticsRun)
    fun recordSensors(run: DiagnosticsRun)
    fun setSelectedFloors(floorIds: List<String>)
    fun markValidationRun()
    fun attachReport(reportId: String)
    fun updateChecklist(entryId: String, state: ChecklistState, note: String): Outcome<Unit>
}

interface InstallationReportRepository {
    val reports: StateFlow<List<InstallationReport>>

    /** Builds, stores and returns a report for the current state of [installation]. */
    fun generate(installation: Installation): InstallationReport
    fun render(report: InstallationReport): String
    fun delete(reportId: String): Outcome<Unit>
}

/**
 * Local export / restore of the app's own data in a versioned JSON format, plus reset of the demo data.
 * Restore validates the whole file before anything is changed.
 */
interface BackupRepository {
    /** The backup file text for the current data. */
    fun export(appVersion: String): String

    /** Validates [json] and describes it, without changing any data. */
    fun inspect(json: String): Outcome<com.sakarrobotics.installation.core.backup.BackupPreview>

    /** Validates [json] and, only if it is valid, applies it. */
    fun restore(json: String, mode: com.sakarrobotics.installation.core.backup.RestoreMode):
        Outcome<com.sakarrobotics.installation.core.backup.RestoreSummary>

    /** Deletes all saved demo data (floors back to the defaults). */
    fun resetDemoData()
}
