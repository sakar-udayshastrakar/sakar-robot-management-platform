package com.sakarrobotics.installation.core.backup

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import com.sakarrobotics.installation.core.data.NameRules
import com.sakarrobotics.installation.core.model.ChecklistEntry
import com.sakarrobotics.installation.core.model.ChecklistState
import com.sakarrobotics.installation.core.model.DiagnosticItem
import com.sakarrobotics.installation.core.model.DiagnosticsRun
import com.sakarrobotics.installation.core.model.ErrorCode
import com.sakarrobotics.installation.core.model.Floor
import com.sakarrobotics.installation.core.model.Installation
import com.sakarrobotics.installation.core.model.InstallationReport
import com.sakarrobotics.installation.core.model.OccupancyGrid
import com.sakarrobotics.installation.core.model.Outcome
import com.sakarrobotics.installation.core.model.ReportChecklistItem
import com.sakarrobotics.installation.core.model.ReportFloor
import com.sakarrobotics.installation.core.model.ReportMap
import com.sakarrobotics.installation.core.model.SimMap
import com.sakarrobotics.installation.core.data.LocalInstallationRepository

/** A problem found while validating a backup. [message] is written for the person who picked the file. */
internal class BackupProblem(val code: ErrorCode, message: String) : Exception(message)

/**
 * Parses and strictly validates backup text. Nothing here touches app state: the caller only changes
 * anything after this returns a [ParsedBackup].
 *
 * Gson can leave "non-null" Kotlin properties null when a field is missing, so every value read from
 * the file is checked explicitly and objects are rebuilt from checked values.
 */
internal object BackupParser {

    private val gson = Gson()
    private val idPattern = Regex("[A-Za-z0-9._-]{1,64}")
    private const val GRID_SYMBOLS = "?.#"

    fun parse(text: String): Outcome<ParsedBackup> = try {
        Outcome.Success(parseOrThrow(text))
    } catch (p: BackupProblem) {
        Outcome.Failure(p.code, p.message ?: "The backup file is not valid.")
    } catch (e: StackOverflowError) {
        Outcome.Failure(ErrorCode.INVALID_BACKUP, "The backup file is not valid (it is nested too deeply).")
    }

    private fun invalid(message: String) = BackupProblem(ErrorCode.INVALID_BACKUP, message)

    private fun parseOrThrow(text: String): ParsedBackup {
        if (text.isBlank()) throw invalid("The file is empty.")
        if (text.length > BackupFormat.MAX_CHARS) throw invalid("The file is too large to be a backup from this app.")

        val root = try {
            JsonParser.parseString(text)
        } catch (e: JsonParseException) {
            throw invalid("The file is not valid JSON. It may be damaged or cut off.")
        }
        if (!root.isJsonObject) throw invalid("This file is not a Sakar Installation Assistant backup.")
        val obj = root.asJsonObject

        if (obj.str("format") != BackupFormat.FORMAT_ID) {
            throw invalid("This file is not a Sakar Installation Assistant backup.")
        }
        val version = obj.int("formatVersion") ?: throw invalid("The backup has no readable format version.")
        if (version < 1) throw invalid("The backup has an invalid format version ($version).")
        if (version > BackupFormat.VERSION) {
            throw BackupProblem(
                ErrorCode.INCOMPATIBLE_BACKUP,
                "This backup was made by a newer version of the app (format $version). " +
                    "This version understands format ${BackupFormat.VERSION} and older. Update the app to restore it."
            )
        }
        val data = obj.get("data")?.takeIf { it.isJsonObject }?.asJsonObject
            ?: throw invalid("The backup has no data section.")

        val floors = parseFloors(data.array("floors"))
        val floorIds = floors.map { it.id }.toSet()
        val maps = parseMaps(data.array("maps"), floorIds)
        val reports = parseReports(data.array("reports"))
        val installationElement = data.get("installation")
        val installation = if (installationElement == null || installationElement.isJsonNull) {
            null
        } else {
            parseInstallation(installationElement, floorIds)
        }

        return ParsedBackup(
            preview = BackupPreview(
                formatVersion = version,
                exportedAtMillis = obj.long("exportedAtMillis") ?: 0L,
                appVersion = obj.str("appVersion")?.take(40),
                floorCount = floors.size,
                mapCount = maps.size,
                reportCount = reports.size,
                installationId = installation?.id
            ),
            floors = floors,
            maps = maps,
            installation = installation,
            reports = reports
        )
    }

    // ------------------------------------------------------------------ floors / maps

    private fun parseFloors(array: JsonArray): List<Floor> {
        if (array.size() > NameRules.MAX_FLOORS) {
            throw invalid("The backup has more than ${NameRules.MAX_FLOORS} floors.")
        }
        val seenIds = HashSet<String>()
        val seenNames = HashSet<String>()
        return array.mapIndexed { i, el ->
            val where = "Floor ${i + 1}"
            val raw = read(el, Floor::class.java, where)
            val id = validId(raw.id as String?, where)
            if (!seenIds.add(id)) throw invalid("$where: the floor id \"$id\" appears more than once.")
            val name = validName(raw.name as String?, "floor", where)
            if (!seenNames.add(name.lowercase())) throw invalid("$where: the floor name \"$name\" appears more than once.")
            if (raw.level < 0 || raw.level > 1000) throw invalid("$where: the floor level is out of range.")
            Floor(id, name, raw.level)
        }
    }

    private fun parseMaps(array: JsonArray, floorIds: Set<String>): List<SimMap> {
        if (array.size() > NameRules.MAX_MAPS) throw invalid("The backup has more than ${NameRules.MAX_MAPS} maps.")
        val seenIds = HashSet<String>()
        val seenNames = HashSet<String>()
        return array.mapIndexed { i, el ->
            val where = "Map ${i + 1}"
            val raw = read(el, SimMap::class.java, where)
            val id = validId(raw.id as String?, where)
            if (!seenIds.add(id)) throw invalid("$where: the map id \"$id\" appears more than once.")
            val name = validName(raw.name as String?, "map", where)
            val floorId = validId(raw.floorId as String?, where)
            if (floorId !in floorIds) throw invalid("$where (\"$name\"): it refers to a floor that is not in the backup.")
            if (!seenNames.add("$floorId/${name.lowercase()}")) {
                throw invalid("$where: the map name \"$name\" appears more than once on the same floor.")
            }
            val status = raw.status as com.sakarrobotics.installation.core.model.MapStatus?
                ?: throw invalid("$where (\"$name\"): the map status is missing or unknown.")
            if (raw.coveragePercent !in 0..100) throw invalid("$where (\"$name\"): the coverage must be between 0 and 100.")
            if (raw.createdAtMillis < 0) throw invalid("$where (\"$name\"): the creation time is invalid.")
            SimMap(id, name, floorId, raw.createdAtMillis, status, raw.coveragePercent, validGrid(raw.preview as OccupancyGrid?, where))
        }
    }

    private fun validGrid(grid: OccupancyGrid?, where: String): OccupancyGrid {
        val rows = (grid?.rows as List<String?>?) ?: throw invalid("$where: the map preview is missing.")
        if (rows.isEmpty() || rows.size > BackupFormat.MAX_GRID_ROWS) throw invalid("$where: the map preview has an unsupported size.")
        val width = rows.first()?.length ?: 0
        if (width == 0 || width > BackupFormat.MAX_GRID_COLUMNS) throw invalid("$where: the map preview has an unsupported size.")
        val clean = rows.map { row ->
            if (row == null || row.length != width) throw invalid("$where: the map preview rows have different lengths.")
            if (row.any { it !in GRID_SYMBOLS }) throw invalid("$where: the map preview contains unsupported symbols.")
            row
        }
        return OccupancyGrid(clean)
    }

    // ------------------------------------------------------------------ reports

    private fun parseReports(array: JsonArray): List<InstallationReport> {
        if (array.size() > com.sakarrobotics.installation.core.data.LocalInstallationReportRepository.MAX_REPORTS) {
            throw invalid("The backup has more reports than this app keeps.")
        }
        val seen = HashSet<String>()
        return array.mapIndexed { i, el ->
            val where = "Report ${i + 1}"
            val r = read(el, InstallationReport::class.java, where)
            val id = validId(r.id as String?, where)
            if (!seen.add(id)) throw invalid("$where: the report id \"$id\" appears more than once.")
            val floorsRaw = (r.floors as List<ReportFloor?>?) ?: throw invalid("$where: the floor list is missing.")
            val checklistRaw = (r.checklist as List<ReportChecklistItem?>?) ?: throw invalid("$where: the checklist is missing.")
            val warningsRaw = (r.warnings as List<String?>?) ?: throw invalid("$where: the warning list is missing.")
            InstallationReport(
                id = id,
                installationId = validId(r.installationId as String?, where),
                generatedAtMillis = r.generatedAtMillis.also { if (it < 0) throw invalid("$where: the time is invalid.") },
                floors = floorsRaw.map { f ->
                    val name = text((f?.name as String?), where, "floor name")
                    val maps = ((f?.maps as List<ReportMap?>?) ?: throw invalid("$where: a floor has no map list.")).map { m ->
                        ReportMap(
                            text(m?.name as String?, where, "map name"),
                            text(m?.status as String?, where, "map status"),
                            (m?.coveragePercent ?: 0).also { if (it !in 0..100) throw invalid("$where: a coverage value is out of range.") }
                        )
                    }
                    ReportFloor(name, maps)
                },
                mappingStatus = text(r.mappingStatus as String?, where, "mapping status"),
                diagnosticsSummary = text(r.diagnosticsSummary as String?, where, "diagnostics summary"),
                checklist = checklistRaw.map { c ->
                    ReportChecklistItem(
                        text(c?.title as String?, where, "checklist title"),
                        c?.required ?: false,
                        text(c?.state as String?, where, "checklist state"),
                        optionalText(c?.note as String?, where, "checklist note")
                    )
                },
                warnings = warningsRaw.map { text(it, where, "warning") },
                simulationNotice = text(r.simulationNotice as String?, where, "simulation notice"),
                completionStatus = (r.completionStatus as String?)?.let { text(it, where, "completion status") }
            )
        }
    }

    // ------------------------------------------------------------------ installation

    private fun parseInstallation(element: JsonElement, floorIds: Set<String>): Installation {
        val where = "The installation record"
        if (!element.isJsonObject) throw invalid("$where is not valid.")
        val raw = read(element, Installation::class.java, where)
        val checklistRaw = (raw.checklist as List<ChecklistEntry?>?) ?: throw invalid("$where has no checklist.")
        val seen = HashSet<String>()
        val checklist = checklistRaw.mapIndexed { i, entry ->
            val w = "$where, checklist item ${i + 1}"
            val c = entry ?: throw invalid("$w is empty.")
            val id = validId(c.id as String?, w)
            if (!seen.add(id)) throw invalid("$w: the id \"$id\" appears more than once.")
            val state = (c.state as ChecklistState?) ?: throw invalid("$w: the state is missing or unknown.")
            val note = optionalText(c.note as String?, w, "note").take(LocalInstallationRepository.MAX_NOTE)
            if ((state == ChecklistState.BLOCKED || state == ChecklistState.NOT_APPLICABLE) && note.isBlank()) {
                throw invalid("$w: a ${state.label.lowercase()} item needs a note.")
            }
            ChecklistEntry(id, text(c.title as String?, w, "title"), optionalText(c.description as String?, w, "description"), c.required, state, note)
        }
        val selected = ((raw.selectedFloorIds as List<String?>?) ?: emptyList()).map { validId(it, where) }.distinct()
        selected.firstOrNull { it !in floorIds }?.let { throw invalid("$where selects a floor that is not in the backup.") }

        return Installation(
            id = validId(raw.id as String?, where),
            createdAtMillis = raw.createdAtMillis.also { if (it < 0) throw invalid("$where: the creation time is invalid.") },
            overviewAcknowledged = raw.overviewAcknowledged,
            readiness = (raw.readiness as DiagnosticsRun?)?.let { validRun(it, "$where, readiness") },
            sensors = (raw.sensors as DiagnosticsRun?)?.let { validRun(it, "$where, sensor diagnostics") },
            selectedFloorIds = selected,
            validationRunAtMillis = raw.validationRunAtMillis,
            reportId = (raw.reportId as String?)?.let { validId(it, where) },
            checklist = checklist
        )
    }

    private fun validRun(run: DiagnosticsRun, where: String): DiagnosticsRun {
        val scenario = run.scenario as com.sakarrobotics.installation.core.model.SimulationScenario?
            ?: throw invalid("$where: the scenario is missing or unknown.")
        val items = (run.items as List<DiagnosticItem?>?) ?: throw invalid("$where: the results are missing.")
        return DiagnosticsRun(
            runAtMillis = run.runAtMillis.also { if (it < 0) throw invalid("$where: the time is invalid.") },
            scenario = scenario,
            items = items.map { item ->
                if (item == null) throw invalid("$where: a result is empty.")
                val steps = (item.troubleshooting as List<String?>?) ?: throw invalid("$where: a result has no guidance list.")
                DiagnosticItem(
                    id = validId(item.id as String?, where),
                    category = (item.category as com.sakarrobotics.installation.core.model.DiagnosticCategory?)
                        ?: throw invalid("$where: a result has an unknown category."),
                    title = text(item.title as String?, where, "result title"),
                    state = (item.state as com.sakarrobotics.installation.core.model.DiagnosticState?)
                        ?: throw invalid("$where: a result has an unknown state."),
                    message = text(item.message as String?, where, "result message"),
                    troubleshooting = steps.map { text(it, where, "guidance") }
                )
            }
        )
    }

    // ------------------------------------------------------------------ small helpers

    private fun <T> read(element: JsonElement, type: Class<T>, where: String): T {
        if (!element.isJsonObject) throw invalid("$where is not valid.")
        return try {
            gson.fromJson(element, type) ?: throw invalid("$where is empty.")
        } catch (e: JsonParseException) {
            throw invalid("$where has a field with the wrong type.")
        }
    }

    private fun validId(value: String?, where: String): String {
        if (value == null || !idPattern.matches(value)) throw invalid("$where has a missing or invalid id.")
        return value
    }

    private fun validName(value: String?, what: String, where: String): String {
        if (value == null) throw invalid("$where: the $what name is missing.")
        return when (val result = NameRules.validate(value, what)) {
            is Outcome.Success -> result.value
            is Outcome.Failure -> throw invalid("$where: ${result.message}")
        }
    }

    private fun text(value: String?, where: String, what: String): String {
        if (value == null) throw invalid("$where: the $what is missing.")
        if (value.length > BackupFormat.MAX_TEXT) throw invalid("$where: the $what is too long.")
        return value
    }

    private fun optionalText(value: String?, where: String, what: String): String =
        if (value == null) "" else text(value, where, what)

    private fun JsonObject.array(name: String): JsonArray =
        get(name)?.takeIf { it.isJsonArray }?.asJsonArray ?: throw invalid("The backup has no \"$name\" list.")

    private fun JsonObject.str(name: String): String? =
        get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString

    private fun JsonObject.int(name: String): Int? = try {
        get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asBigDecimal?.intValueExact()
    } catch (e: ArithmeticException) {
        null
    }

    private fun JsonObject.long(name: String): Long? = try {
        get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asBigDecimal?.longValueExact()
    } catch (e: ArithmeticException) {
        null
    }
}
