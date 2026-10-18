package com.sakarrobotics.installation.core

import com.sakarrobotics.installation.core.backup.BackupService
import com.sakarrobotics.installation.core.data.LocalFloorRepository
import com.sakarrobotics.installation.core.data.LocalInstallationReportRepository
import com.sakarrobotics.installation.core.data.LocalInstallationRepository
import com.sakarrobotics.installation.core.data.SimMapStore
import com.sakarrobotics.installation.core.data.SimulatedDiagnosticsRepository
import com.sakarrobotics.installation.core.data.SimulatedMappingRepository
import com.sakarrobotics.installation.core.model.InstallationReport
import com.sakarrobotics.installation.core.repository.BackupRepository
import com.sakarrobotics.installation.core.repository.DiagnosticsRepository
import com.sakarrobotics.installation.core.repository.FloorRepository
import com.sakarrobotics.installation.core.repository.InstallationReportRepository
import com.sakarrobotics.installation.core.repository.InstallationRepository
import com.sakarrobotics.installation.core.repository.MappingRepository
import com.sakarrobotics.installation.core.storage.Clock
import com.sakarrobotics.installation.core.storage.IdGenerator
import com.sakarrobotics.installation.core.storage.KeyValueStore
import com.sakarrobotics.installation.core.storage.MonitoredKeyValueStore
import com.sakarrobotics.installation.core.storage.PersistenceMonitor
import com.sakarrobotics.installation.core.storage.RandomIdGenerator
import com.sakarrobotics.installation.core.storage.SystemClock
import kotlinx.coroutines.CoroutineScope

/**
 * Composition root: wires the repositories to their SIMULATED/local implementations.
 * Nothing here can reach hardware - there is no hardware-backed implementation to wire in.
 */
class AssistantServices(
    store: KeyValueStore,
    scope: CoroutineScope,
    clock: Clock = SystemClock,
    ids: IdGenerator = RandomIdGenerator,
    mappingTickMillis: Long = SimulatedMappingRepository.DEFAULT_TICK_MILLIS,
    diagnosticsLatencyMillis: Long = SimulatedDiagnosticsRepository.DEFAULT_LATENCY_MILLIS
) {
    /** Reports storage failures (e.g. a full disk) so the UI can warn instead of silently losing data. */
    val persistence = PersistenceMonitor()

    private val monitoredStore = MonitoredKeyValueStore(store, persistence)
    private val mapStore = SimMapStore(monitoredStore)

    private val floorRepo = LocalFloorRepository(monitoredStore, mapStore, ids)
    private val mappingRepo = SimulatedMappingRepository(mapStore, floorRepo, clock, ids, scope, mappingTickMillis)
    private val diagnosticsRepo = SimulatedDiagnosticsRepository(monitoredStore, clock, diagnosticsLatencyMillis)
    private val installationRepo = LocalInstallationRepository(monitoredStore, clock, ids)
    private val reportRepo = LocalInstallationReportRepository(monitoredStore, clock, ids, floorRepo, mapStore)

    val floors: FloorRepository = floorRepo
    val mapping: MappingRepository = mappingRepo
    val diagnostics: DiagnosticsRepository = diagnosticsRepo
    val installations: InstallationRepository = installationRepo
    val reports: InstallationReportRepository = reportRepo
    val backup: BackupRepository = BackupService(
        floorRepo, mapStore, mappingRepo, installationRepo, reportRepo, diagnosticsRepo, clock
    )

    /** Generates a report for the current installation and links it to that installation. */
    fun generateReport(): InstallationReport {
        val installation = installations.ensureCurrent()
        val report = reports.generate(installation)
        installations.attachReport(report.id)
        return report
    }

    /** Stops background work (the simulation timer). Safe to call more than once. */
    fun close() {
        mapping.shutdown()
    }
}
