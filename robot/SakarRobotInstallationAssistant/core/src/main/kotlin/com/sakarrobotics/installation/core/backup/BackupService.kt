package com.sakarrobotics.installation.core.backup

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.sakarrobotics.installation.core.data.LocalFloorRepository
import com.sakarrobotics.installation.core.data.LocalInstallationReportRepository
import com.sakarrobotics.installation.core.data.LocalInstallationRepository
import com.sakarrobotics.installation.core.data.NameRules
import com.sakarrobotics.installation.core.data.SimMapStore
import com.sakarrobotics.installation.core.data.SimulatedDiagnosticsRepository
import com.sakarrobotics.installation.core.model.Floor
import com.sakarrobotics.installation.core.model.Outcome
import com.sakarrobotics.installation.core.model.SimMap
import com.sakarrobotics.installation.core.repository.BackupRepository
import com.sakarrobotics.installation.core.repository.MappingRepository
import com.sakarrobotics.installation.core.storage.Clock

/** Exports, validates and restores the app's local demo data. Nothing here contacts a robot or a network. */
class BackupService(
    private val floors: LocalFloorRepository,
    private val mapStore: SimMapStore,
    private val mapping: MappingRepository,
    private val installations: LocalInstallationRepository,
    private val reports: LocalInstallationReportRepository,
    private val diagnostics: SimulatedDiagnosticsRepository,
    private val clock: Clock
) : BackupRepository {

    private val gson = GsonBuilder().setPrettyPrinting().create()

    override fun export(appVersion: String): String {
        val data = JsonObject().apply {
            add("floors", gson.toJsonTree(floors.floors.value))
            add("maps", gson.toJsonTree(mapStore.maps.value))
            add("installation", gson.toJsonTree(installations.current.value))
            add("reports", gson.toJsonTree(reports.reports.value))
        }
        val root = JsonObject().apply {
            addProperty("format", BackupFormat.FORMAT_ID)
            addProperty("formatVersion", BackupFormat.VERSION)
            addProperty("appVersion", appVersion)
            addProperty("exportedAtMillis", clock.nowMillis())
            addProperty("simulationOnly", true)
            addProperty("mapFormat", BackupFormat.MAP_FORMAT)
            addProperty("notice", BackupFormat.NOTICE)
            add("data", data)
        }
        return gson.toJson(root)
    }

    override fun inspect(json: String): Outcome<BackupPreview> =
        when (val parsed = BackupParser.parse(json)) {
            is Outcome.Success -> Outcome.Success(parsed.value.preview)
            is Outcome.Failure -> parsed
        }

    override fun restore(json: String, mode: RestoreMode): Outcome<RestoreSummary> {
        val backup = when (val parsed = BackupParser.parse(json)) {
            is Outcome.Success -> parsed.value
            is Outcome.Failure -> return parsed
        }
        return Outcome.Success(if (mode == RestoreMode.REPLACE) replace(backup) else merge(backup))
    }

    // ------------------------------------------------------------------ replace

    private fun replace(backup: ParsedBackup): RestoreSummary {
        mapping.resetSession()
        mapping.closeLoadedMap()
        floors.replaceAll(backup.floors)
        mapStore.replaceAll(backup.maps)
        installations.replaceCurrent(backup.installation)
        reports.replaceAll(backup.reports)
        return RestoreSummary(
            mode = RestoreMode.REPLACE,
            floorsAdded = backup.floors.size, floorsMatched = 0,
            mapsAdded = backup.maps.size, mapsSkipped = 0,
            reportsAdded = backup.reports.size, reportsSkipped = 0,
            installationRestored = backup.installation != null,
            notes = emptyList()
        )
    }

    // ------------------------------------------------------------------ merge (never changes existing records)

    private fun merge(backup: ParsedBackup): RestoreSummary {
        val notes = mutableListOf<String>()

        // Floors: match by id, then by name; only genuinely new floors are added.
        val currentFloors = floors.floors.value
        val idMap = HashMap<String, String>()
        val newFloors = mutableListOf<Floor>()
        var nextLevel = (currentFloors.maxOfOrNull { it.level } ?: -1) + 1
        var matched = 0
        for (floor in backup.floors) {
            val sameId = currentFloors.firstOrNull { it.id == floor.id }
            val sameName = currentFloors.firstOrNull { it.name.equals(floor.name, ignoreCase = true) }
            when {
                sameId != null -> { idMap[floor.id] = sameId.id; matched++ }
                sameName != null -> { idMap[floor.id] = sameName.id; matched++ }
                currentFloors.size + newFloors.size >= NameRules.MAX_FLOORS -> {
                    notes += "Floor \"${floor.name}\" was skipped: the floor limit (${NameRules.MAX_FLOORS}) is reached."
                }
                else -> {
                    idMap[floor.id] = floor.id
                    newFloors += floor.copy(level = nextLevel++)
                }
            }
        }

        // Maps: skip existing ids and name clashes on the same floor.
        val currentMaps = mapStore.maps.value
        val newMaps = mutableListOf<SimMap>()
        var skippedMaps = 0
        for (map in backup.maps) {
            val targetFloor = idMap[map.floorId]
            val clash = currentMaps.any { it.id == map.id } ||
                newMaps.any { it.id == map.id } ||
                (targetFloor != null && (currentMaps + newMaps).any {
                    it.floorId == targetFloor && it.name.equals(map.name, ignoreCase = true)
                })
            if (targetFloor == null || clash || currentMaps.size + newMaps.size >= NameRules.MAX_MAPS) {
                skippedMaps++
            } else {
                newMaps += map.copy(floorId = targetFloor)
            }
        }

        // Reports: skip existing ids; keep the newest within the app's limit.
        val currentReports = reports.reports.value
        val freshReports = backup.reports.filter { r -> currentReports.none { it.id == r.id } }
        val skippedReports = backup.reports.size - freshReports.size
        val combined = (currentReports + freshReports)
            .sortedByDescending { it.generatedAtMillis }
            .take(LocalInstallationReportRepository.MAX_REPORTS)
        val reportsAdded = freshReports.count { r -> combined.any { it.id == r.id } }

        // Installation: adopt the backup's only when there is no current one; never overwrite work in progress.
        val current = installations.current.value
        var installationRestored = false
        val incoming = backup.installation
        if (incoming != null) {
            when {
                current == null -> {
                    installations.replaceCurrent(
                        incoming.copy(selectedFloorIds = incoming.selectedFloorIds.mapNotNull { idMap[it] }.distinct())
                    )
                    installationRestored = true
                }
                current.id != incoming.id -> notes +=
                    "Installation ${incoming.id} was not restored because installation ${current.id} is in progress. " +
                        "Use \"Replace all data\" to restore it."
            }
        }

        // Commit only after everything was computed.
        if (newFloors.isNotEmpty()) floors.replaceAll(currentFloors + newFloors)
        if (newMaps.isNotEmpty()) mapStore.replaceAll(currentMaps + newMaps)
        if (reportsAdded > 0 || combined.size != currentReports.size) reports.replaceAll(combined)

        return RestoreSummary(
            mode = RestoreMode.MERGE,
            floorsAdded = newFloors.size, floorsMatched = matched,
            mapsAdded = newMaps.size, mapsSkipped = skippedMaps,
            reportsAdded = reportsAdded, reportsSkipped = skippedReports + (freshReports.size - reportsAdded),
            installationRestored = installationRestored,
            notes = notes
        )
    }

    // ------------------------------------------------------------------ reset

    override fun resetDemoData() {
        mapping.resetSession()
        mapping.closeLoadedMap()
        floors.resetToDefaults()
        mapStore.replaceAll(emptyList())
        installations.replaceCurrent(null)
        reports.replaceAll(emptyList())
        diagnostics.resetAll()
    }
}
