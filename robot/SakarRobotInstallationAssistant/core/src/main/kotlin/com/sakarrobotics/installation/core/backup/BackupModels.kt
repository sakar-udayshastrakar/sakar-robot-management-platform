package com.sakarrobotics.installation.core.backup

import com.sakarrobotics.installation.core.model.Floor
import com.sakarrobotics.installation.core.model.Installation
import com.sakarrobotics.installation.core.model.InstallationReport
import com.sakarrobotics.installation.core.model.SimMap

/**
 * The on-disk backup format. It is versioned so a future release can read older files, and so this
 * release can refuse files from a newer one instead of misreading them.
 *
 * The file holds this app's own demo data only. Maps inside it are Sakar demo occupancy grids
 * ([MAP_FORMAT]); they are NOT Keenon maps and are not compatible with any robot map format.
 */
object BackupFormat {
    const val FORMAT_ID = "sakar-installation-assistant-backup"
    const val VERSION = 1
    const val MAP_FORMAT = "sakar-demo-occupancy-text-v1"
    const val MAX_CHARS = 5_000_000
    const val MAX_GRID_ROWS = 64
    const val MAX_GRID_COLUMNS = 128
    const val MAX_TEXT = 2_000
    const val NOTICE = "SIMULATION ONLY. This file contains demo data from the Sakar Robot Installation Assistant. " +
        "Its maps are Sakar demo occupancy grids and are not compatible with Keenon or any real robot map format."
}

enum class RestoreMode {
    /** Adds what is missing and never changes or removes existing data. */
    MERGE,

    /** Replaces all current data with the backup (destructive; the UI asks for confirmation). */
    REPLACE
}

/** What a backup file contains, shown to the user before anything is changed. */
data class BackupPreview(
    val formatVersion: Int,
    val exportedAtMillis: Long,
    val appVersion: String?,
    val floorCount: Int,
    val mapCount: Int,
    val reportCount: Int,
    val installationId: String?
)

data class RestoreSummary(
    val mode: RestoreMode,
    val floorsAdded: Int,
    val floorsMatched: Int,
    val mapsAdded: Int,
    val mapsSkipped: Int,
    val reportsAdded: Int,
    val reportsSkipped: Int,
    val installationRestored: Boolean,
    val notes: List<String>
) {
    val changedAnything: Boolean
        get() = floorsAdded > 0 || mapsAdded > 0 || reportsAdded > 0 || installationRestored || mode == RestoreMode.REPLACE

    fun describe(): String = buildString {
        append(if (mode == RestoreMode.REPLACE) "Replaced all data with the backup. " else "Merged the backup into the current data. ")
        append("Floors added: $floorsAdded (already present: $floorsMatched). ")
        append("Maps added: $mapsAdded (skipped: $mapsSkipped). ")
        append("Reports added: $reportsAdded (skipped: $reportsSkipped). ")
        append(if (installationRestored) "Installation record restored." else "Installation record unchanged.")
        notes.forEach { append(" ").append(it) }
    }
}

/** A fully validated backup, ready to apply. Only the backup service creates one. */
internal data class ParsedBackup(
    val preview: BackupPreview,
    val floors: List<Floor>,
    val maps: List<SimMap>,
    val installation: Installation?,
    val reports: List<InstallationReport>
)
