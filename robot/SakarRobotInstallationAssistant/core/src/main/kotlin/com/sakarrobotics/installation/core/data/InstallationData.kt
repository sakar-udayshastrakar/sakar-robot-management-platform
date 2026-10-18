package com.sakarrobotics.installation.core.data

import com.sakarrobotics.installation.core.logic.DefaultChecklist
import com.sakarrobotics.installation.core.logic.ReportBuilder
import com.sakarrobotics.installation.core.model.ChecklistState
import com.sakarrobotics.installation.core.model.DiagnosticsRun
import com.sakarrobotics.installation.core.model.ErrorCode
import com.sakarrobotics.installation.core.model.Installation
import com.sakarrobotics.installation.core.model.InstallationReport
import com.sakarrobotics.installation.core.model.Outcome
import com.sakarrobotics.installation.core.repository.FloorRepository
import com.sakarrobotics.installation.core.repository.InstallationReportRepository
import com.sakarrobotics.installation.core.repository.InstallationRepository
import com.sakarrobotics.installation.core.storage.Clock
import com.sakarrobotics.installation.core.storage.IdGenerator
import com.sakarrobotics.installation.core.storage.Json
import com.sakarrobotics.installation.core.storage.KeyValueStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Local persistence of the current installation (wizard progress, selections, checklist). Demo data only. */
class LocalInstallationRepository(
    private val store: KeyValueStore,
    private val clock: Clock,
    private val ids: IdGenerator
) : InstallationRepository {

    private val _current = MutableStateFlow(Json.loadOrNull<Installation>(store, KEY))
    override val current: StateFlow<Installation?> = _current.asStateFlow()

    override fun startNew(): Installation {
        val installation = Installation(
            id = ids.next("INST").uppercase(),
            createdAtMillis = clock.nowMillis(),
            checklist = DefaultChecklist.entries()
        )
        commit(installation)
        return installation
    }

    override fun ensureCurrent(): Installation = _current.value ?: startNew()

    override fun acknowledgeOverview() = modify { it.copy(overviewAcknowledged = true) }
    override fun recordReadiness(run: DiagnosticsRun) = modify { it.copy(readiness = run) }
    override fun recordSensors(run: DiagnosticsRun) = modify { it.copy(sensors = run) }
    override fun setSelectedFloors(floorIds: List<String>) = modify { it.copy(selectedFloorIds = floorIds.distinct()) }
    override fun markValidationRun() = modify { it.copy(validationRunAtMillis = clock.nowMillis()) }
    override fun attachReport(reportId: String) = modify { it.copy(reportId = reportId) }

    override fun updateChecklist(entryId: String, state: ChecklistState, note: String): Outcome<Unit> {
        val installation = _current.value
            ?: return Outcome.Failure(ErrorCode.NO_INSTALLATION, "Start an installation first.")
        if (installation.checklist.none { it.id == entryId }) {
            return Outcome.Failure(ErrorCode.NOT_FOUND, "That checklist item does not exist.")
        }
        val cleanNote = note.trim()
        if ((state == ChecklistState.BLOCKED || state == ChecklistState.NOT_APPLICABLE) && cleanNote.isEmpty()) {
            return Outcome.Failure(
                ErrorCode.NOTE_REQUIRED,
                "Add a note explaining why this item is ${state.label.lowercase()}."
            )
        }
        if (cleanNote.length > MAX_NOTE) {
            return Outcome.Failure(ErrorCode.INVALID_NAME, "Notes must be $MAX_NOTE characters or fewer.")
        }
        commit(
            installation.copy(
                checklist = installation.checklist.map {
                    if (it.id == entryId) it.copy(state = state, note = cleanNote) else it
                }
            )
        )
        return Outcome.Success(Unit)
    }

    /** Replaces (or clears) the current installation. Used by backup restore and reset; callers validate first. */
    fun replaceCurrent(installation: Installation?) {
        _current.value = installation
        Json.save(store, KEY, installation)
    }

    private fun modify(change: (Installation) -> Installation) {
        val installation = _current.value ?: return
        commit(change(installation))
    }

    private fun commit(installation: Installation) {
        _current.value = installation
        Json.save(store, KEY, installation)
    }

    companion object {
        const val MAX_NOTE = 200
        private const val KEY = "installation.v1"
    }
}

class LocalInstallationReportRepository(
    private val store: KeyValueStore,
    private val clock: Clock,
    private val ids: IdGenerator,
    private val floors: FloorRepository,
    private val mapStore: SimMapStore
) : InstallationReportRepository {

    private val _reports = MutableStateFlow(
        Json.loadList(store, KEY, Array<InstallationReport>::class.java) { emptyList() }
    )
    override val reports: StateFlow<List<InstallationReport>> = _reports.asStateFlow()

    override fun generate(installation: Installation): InstallationReport {
        val report = ReportBuilder.build(
            reportId = ids.next("RPT").uppercase(),
            now = clock.nowMillis(),
            installation = installation,
            floors = floors.floors.value,
            maps = mapStore.maps.value
        )
        val next = (listOf(report) + _reports.value).take(MAX_REPORTS)
        _reports.value = next
        Json.save(store, KEY, next)
        return report
    }

    override fun render(report: InstallationReport): String = ReportBuilder.render(report)

    /** Replaces the saved reports (backup restore / reset). Callers validate first. */
    fun replaceAll(next: List<InstallationReport>) {
        val capped = next.take(MAX_REPORTS)
        _reports.value = capped
        Json.save(store, KEY, capped)
    }

    override fun delete(reportId: String): Outcome<Unit> {
        if (_reports.value.none { it.id == reportId }) {
            return Outcome.Failure(ErrorCode.NOT_FOUND, "That report no longer exists.")
        }
        val next = _reports.value.filterNot { it.id == reportId }
        _reports.value = next
        Json.save(store, KEY, next)
        return Outcome.Success(Unit)
    }

    companion object {
        const val MAX_REPORTS = 20
        private const val KEY = "reports.v1"
    }
}
