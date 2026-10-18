package com.sakarrobotics.installation.core

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.sakarrobotics.installation.core.backup.BackupFormat
import com.sakarrobotics.installation.core.backup.RestoreMode
import com.sakarrobotics.installation.core.model.ChecklistState
import com.sakarrobotics.installation.core.model.ErrorCode
import com.sakarrobotics.installation.core.model.Outcome
import com.sakarrobotics.installation.core.storage.InMemoryKeyValueStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private const val GROUND = "floor-ground"
private val gson = GsonBuilder().create()

private fun populated(): Fixture {
    val f = Fixture()
    f.installations.startNew()
    f.installations.acknowledgeOverview()
    f.installations.setSelectedFloors(listOf(GROUND))
    f.mapFloor(GROUND, "Lobby sweep")
    f.installations.updateChecklist("site-walk", ChecklistState.DONE, "Walked on site")
    f.installations.updateChecklist("wifi", ChecklistState.BLOCKED, "Dead spot near the lift")
    f.services.generateReport()
    return f
}

private fun Fixture.snapshot(): List<Any?> =
    listOf(floors.floors.value, mapping.maps.value, installations.current.value, reports.reports.value)

private fun mutate(json: String, change: (JsonObject) -> Unit): String {
    val root = JsonParser.parseString(json).asJsonObject
    change(root)
    return gson.toJson(root)
}

private fun JsonObject.data(): JsonObject = getAsJsonObject("data")
private fun JsonObject.array(name: String): JsonArray = data().getAsJsonArray(name)

class BackupExportTest {

    @Test
    fun `export is versioned, labelled simulation only and makes no vendor compatibility claim`() {
        val json = populated().services.backup.export("0.2.0-sim")
        val root = JsonParser.parseString(json).asJsonObject
        assertEquals(BackupFormat.FORMAT_ID, root["format"].asString)
        assertEquals(BackupFormat.VERSION, root["formatVersion"].asInt)
        assertEquals("0.2.0-sim", root["appVersion"].asString)
        assertTrue(root["simulationOnly"].asBoolean)
        assertEquals(BackupFormat.MAP_FORMAT, root["mapFormat"].asString)
        val notice = root["notice"].asString
        assertTrue(notice.startsWith("SIMULATION ONLY"))
        assertTrue(notice.contains("not compatible with Keenon"))
    }

    @Test
    fun `export holds floors maps installation and reports and nothing sensitive`() {
        val f = populated()
        val json = f.services.backup.export("0.2.0-sim")
        val root = JsonParser.parseString(json).asJsonObject
        assertEquals(2, root.array("floors").size())
        assertEquals(1, root.array("maps").size())
        assertEquals(1, root.array("reports").size())
        assertNotNull(root.data().get("installation").asJsonObject)
        val lower = json.lowercase()
        listOf("password", "token", "secret", "ssid", "192.168", "android_id").forEach {
            assertFalse(lower.contains(it), "backup must not contain '$it'")
        }
    }
}

class BackupRestoreTest {

    @Test
    fun `replace restores an identical copy of the data into a fresh install`() {
        val source = populated()
        val json = source.services.backup.export("0.2.0-sim")

        val target = Fixture()
        val result = target.services.backup.restore(json, RestoreMode.REPLACE)
        assertTrue(result is Outcome.Success)
        assertEquals(source.snapshot(), target.snapshot())
    }

    @Test
    fun `restored data survives an app restart`() {
        val store = InMemoryKeyValueStore()
        val json = populated().services.backup.export("0.2.0-sim")
        Fixture(store = store).services.backup.restore(json, RestoreMode.REPLACE)

        val restarted = Fixture(store = store)
        assertEquals("Lobby sweep", restarted.mapping.maps.value.single().name)
        assertEquals(1, restarted.reports.reports.value.size)
        assertNotNull(restarted.installations.current.value)
    }

    @Test
    fun `merge twice never creates duplicates`() {
        val json = populated().services.backup.export("0.2.0-sim")
        val target = Fixture()

        val first = (target.services.backup.restore(json, RestoreMode.MERGE) as Outcome.Success).value
        val afterFirst = target.snapshot()
        val second = (target.services.backup.restore(json, RestoreMode.MERGE) as Outcome.Success).value

        assertEquals(afterFirst, target.snapshot(), "a second merge changes nothing")
        assertEquals(1, first.mapsAdded)
        assertEquals(0, second.mapsAdded)
        assertEquals(1, second.mapsSkipped)
        assertEquals(0, second.reportsAdded)
        assertEquals(1, second.reportsSkipped)
        assertEquals(1, target.mapping.maps.value.size)
        assertEquals(2, target.floors.floors.value.size)
    }

    @Test
    fun `merge never overwrites or removes existing data`() {
        val json = populated().services.backup.export("0.2.0-sim")
        val target = Fixture()
        target.mapFloor("floor-first", "Existing first floor map")
        val existingMaps = target.mapping.maps.value
        target.floors.addFloor("Roof")

        target.services.backup.restore(json, RestoreMode.MERGE)

        assertTrue(target.mapping.maps.value.containsAll(existingMaps))
        assertEquals(2, target.mapping.maps.value.size)
        assertTrue(target.floors.floors.value.any { it.name == "Roof" })
    }

    @Test
    fun `merge matches floors by name and moves maps onto the existing floor`() {
        val json = mutate(populated().services.backup.export("0.2.0-sim")) { root ->
            // Same floor name as an existing floor, but a different id (e.g. made on another tablet).
            root.array("floors").forEach { f ->
                if (f.asJsonObject["id"].asString == GROUND) f.asJsonObject.addProperty("id", "floor-other-tablet")
            }
            root.array("maps").forEach { it.asJsonObject.addProperty("floorId", "floor-other-tablet") }
            val selected = root.data().getAsJsonObject("installation").getAsJsonArray("selectedFloorIds")
            selected.set(0, com.google.gson.JsonPrimitive("floor-other-tablet"))
        }
        val target = Fixture()
        val summary = (target.services.backup.restore(json, RestoreMode.MERGE) as Outcome.Success).value

        assertEquals(2, target.floors.floors.value.size, "no duplicate floor was created")
        assertEquals(GROUND, target.mapping.maps.value.single().floorId)
        assertEquals(2, summary.floorsMatched)
        assertEquals(0, summary.floorsAdded)
    }

    @Test
    fun `merge adds floors that do not exist yet on a new level`() {
        val json = mutate(populated().services.backup.export("0.2.0-sim")) { root ->
            root.array("floors").add(JsonParser.parseString("""{"id":"floor-roof","name":"Roof","level":7}"""))
        }
        val target = Fixture()
        target.services.backup.restore(json, RestoreMode.MERGE)
        val roof = target.floors.floors.value.single { it.name == "Roof" }
        assertEquals(2, roof.level, "appended after the existing floors")
    }

    @Test
    fun `merge keeps the installation in progress and explains why the backup one was skipped`() {
        val json = populated().services.backup.export("0.2.0-sim")
        val target = Fixture()
        target.installations.startNew()
        val current = target.installations.startNew() // a different id from the one in the backup

        val summary = (target.services.backup.restore(json, RestoreMode.MERGE) as Outcome.Success).value

        assertEquals(current, target.installations.current.value)
        assertFalse(summary.installationRestored)
        assertTrue(summary.notes.single().contains("is in progress"))
    }

    @Test
    fun `merge adopts the backup installation when there is none`() {
        val source = populated()
        val target = Fixture()
        val summary = (target.services.backup.restore(source.services.backup.export("x"), RestoreMode.MERGE) as Outcome.Success).value
        assertTrue(summary.installationRestored)
        assertEquals(source.installations.current.value, target.installations.current.value)
    }

    @Test
    fun `replace discards current data and stops a running simulated session`() {
        val json = populated().services.backup.export("0.2.0-sim")
        val target = Fixture()
        target.mapFloor("floor-first", "Will be replaced")
        target.mapping.startSession(GROUND)

        target.services.backup.restore(json, RestoreMode.REPLACE)

        assertEquals(listOf("Lobby sweep"), target.mapping.maps.value.map { it.name })
        assertFalse(target.mapping.session.value.isActive)
    }

    @Test
    fun `inspect describes a backup without changing anything`() {
        val json = populated().services.backup.export("0.2.0-sim")
        val target = Fixture()
        val before = target.snapshot()
        val preview = (target.services.backup.inspect(json) as Outcome.Success).value
        assertEquals(before, target.snapshot())
        assertEquals(2, preview.floorCount)
        assertEquals(1, preview.mapCount)
        assertEquals(1, preview.reportCount)
        assertNotNull(preview.installationId)
        assertEquals("0.2.0-sim", preview.appVersion)
    }
}

/** Every malformed, damaged or incompatible file is refused with a message and changes nothing. */
class BackupValidationTest {

    private val valid: String by lazy { populated().services.backup.export("0.2.0-sim") }

    private fun assertRefused(json: String, code: ErrorCode = ErrorCode.INVALID_BACKUP, mention: String? = null) {
        val target = Fixture()
        target.mapFloor(GROUND, "Keep me")
        val before = target.snapshot()
        for (mode in RestoreMode.values()) {
            val result = target.services.backup.restore(json, mode)
            assertTrue(result is Outcome.Failure, "expected failure for $mode")
            result as Outcome.Failure
            assertEquals(code, result.code, result.message)
            assertTrue(result.message.isNotBlank())
            if (mention != null) assertTrue(result.message.contains(mention, ignoreCase = true), result.message)
            assertEquals(before, target.snapshot(), "a refused file must not change any data ($mode)")
        }
        assertTrue(target.services.backup.inspect(json) is Outcome.Failure)
    }

    @Test fun `empty file`() = assertRefused("", mention = "empty")
    @Test fun `whitespace only`() = assertRefused("   \n  ", mention = "empty")
    @Test fun `not json at all`() = assertRefused("this is not json", mention = "not valid JSON")
    @Test fun `truncated file`() = assertRefused(valid.substring(0, valid.length / 2), mention = "not valid JSON")
    @Test fun `json array instead of object`() = assertRefused("[1,2,3]", mention = "not a Sakar")
    @Test fun `some other json object`() = assertRefused("""{"hello":"world"}""", mention = "not a Sakar")
    @Test fun `trailing garbage after the document`() = assertRefused("$valid garbage")

    @Test
    fun `file larger than any real backup`() =
        assertRefused("{" + "x".repeat(BackupFormat.MAX_CHARS) + "}", mention = "too large")

    @Test
    fun `deeply nested document does not crash the parser`() =
        assertRefused("[".repeat(100_000) + "]".repeat(100_000))

    @Test fun `wrong format id`() = assertRefused(mutate(valid) { it.addProperty("format", "something-else") }, mention = "not a Sakar")

    @Test fun `missing format version`() = assertRefused(mutate(valid) { it.remove("formatVersion") }, mention = "version")
    @Test fun `format version zero`() = assertRefused(mutate(valid) { it.addProperty("formatVersion", 0) }, mention = "version")
    @Test fun `format version as text`() = assertRefused(mutate(valid) { it.addProperty("formatVersion", "1") }, mention = "version")
    @Test fun `fractional format version`() = assertRefused(mutate(valid) { it.addProperty("formatVersion", 1.5) }, mention = "version")

    @Test
    fun `newer format version is refused as incompatible`() = assertRefused(
        mutate(valid) { it.addProperty("formatVersion", BackupFormat.VERSION + 1) },
        code = ErrorCode.INCOMPATIBLE_BACKUP, mention = "newer version"
    )

    @Test fun `missing data section`() = assertRefused(mutate(valid) { it.remove("data") }, mention = "data")
    @Test fun `missing floors list`() = assertRefused(mutate(valid) { it.data().remove("floors") }, mention = "floors")
    @Test fun `missing maps list`() = assertRefused(mutate(valid) { it.data().remove("maps") }, mention = "maps")
    @Test fun `missing reports list`() = assertRefused(mutate(valid) { it.data().remove("reports") }, mention = "reports")
    @Test fun `floors is not a list`() = assertRefused(mutate(valid) { it.data().addProperty("floors", "nope") }, mention = "floors")

    @Test
    fun `duplicate floor ids`() = assertRefused(mutate(valid) { root ->
        root.array("floors").forEach { it.asJsonObject.addProperty("id", "same") }
    }, mention = "more than once")

    @Test
    fun `duplicate floor names ignoring case`() = assertRefused(mutate(valid) { root ->
        root.array("floors").forEachIndexed { i, f -> f.asJsonObject.addProperty("name", if (i == 0) "Hall" else "HALL") }
    }, mention = "more than once")

    @Test
    fun `floor with no name`() = assertRefused(mutate(valid) { it.array("floors")[0].asJsonObject.remove("name") }, mention = "name")

    @Test
    fun `floor name that is too long`() = assertRefused(mutate(valid) {
        it.array("floors")[0].asJsonObject.addProperty("name", "x".repeat(41))
    }, mention = "40 characters")

    @Test
    fun `floor id with unsafe characters`() = assertRefused(mutate(valid) {
        it.array("floors")[0].asJsonObject.addProperty("id", "../../etc passwd")
    }, mention = "id")

    @Test
    fun `floor field with the wrong type`() = assertRefused(mutate(valid) {
        it.array("floors")[0].asJsonObject.addProperty("level", "high")
    }, mention = "wrong type")

    @Test
    fun `negative floor level`() = assertRefused(mutate(valid) {
        it.array("floors")[0].asJsonObject.addProperty("level", -3)
    }, mention = "level")

    @Test
    fun `more floors than the app supports`() = assertRefused(mutate(valid) { root ->
        repeat(25) { i -> root.array("floors").add(JsonParser.parseString("""{"id":"f$i","name":"Extra $i","level":${i + 10}}""")) }
    }, mention = "more than")

    @Test
    fun `map that refers to a floor missing from the backup`() = assertRefused(mutate(valid) {
        it.array("maps")[0].asJsonObject.addProperty("floorId", "floor-ghost")
    }, mention = "floor that is not in the backup")

    @Test
    fun `map with an unknown status`() = assertRefused(mutate(valid) {
        it.array("maps")[0].asJsonObject.addProperty("status", "PERFECT")
    }, mention = "status")

    @Test
    fun `map coverage out of range`() = assertRefused(mutate(valid) {
        it.array("maps")[0].asJsonObject.addProperty("coveragePercent", 150)
    }, mention = "coverage")

    @Test
    fun `map preview with unsupported symbols`() = assertRefused(mutate(valid) { root ->
        val rows = root.array("maps")[0].asJsonObject.getAsJsonObject("preview").getAsJsonArray("rows")
        rows.set(0, com.google.gson.JsonPrimitive("X".repeat(rows[0].asString.length)))
    }, mention = "unsupported symbols")

    @Test
    fun `map preview with rows of different lengths`() = assertRefused(mutate(valid) { root ->
        val rows = root.array("maps")[0].asJsonObject.getAsJsonObject("preview").getAsJsonArray("rows")
        rows.set(0, com.google.gson.JsonPrimitive("."))
    }, mention = "different lengths")

    @Test
    fun `map preview that is missing`() = assertRefused(mutate(valid) {
        it.array("maps")[0].asJsonObject.remove("preview")
    }, mention = "preview")

    @Test
    fun `map preview that is far too large`() = assertRefused(mutate(valid) { root ->
        val rows = root.array("maps")[0].asJsonObject.getAsJsonObject("preview").getAsJsonArray("rows")
        repeat(100) { rows.add(rows[0]) }
    }, mention = "unsupported size")

    @Test
    fun `two maps with the same name on one floor`() = assertRefused(mutate(valid) { root ->
        val copy = gson.fromJson(gson.toJson(root.array("maps")[0]), JsonObject::class.java)
        copy.addProperty("id", "map-copy")
        root.array("maps").add(copy)
    }, mention = "more than once")

    @Test
    fun `installation checklist item with an unknown state`() = assertRefused(mutate(valid) { root ->
        root.data().getAsJsonObject("installation").getAsJsonArray("checklist")[0].asJsonObject.addProperty("state", "MAYBE")
    }, mention = "state")

    @Test
    fun `blocked checklist item without the required note`() = assertRefused(mutate(valid) { root ->
        val items = root.data().getAsJsonObject("installation").getAsJsonArray("checklist")
        items.forEach { if (it.asJsonObject["state"].asString == "BLOCKED") it.asJsonObject.addProperty("note", "") }
    }, mention = "needs a note")

    @Test
    fun `installation that selects a floor missing from the backup`() = assertRefused(mutate(valid) { root ->
        root.data().getAsJsonObject("installation").getAsJsonArray("selectedFloorIds").add("floor-ghost")
    }, mention = "selects a floor")

    @Test
    fun `installation without a checklist`() = assertRefused(mutate(valid) {
        it.data().getAsJsonObject("installation").remove("checklist")
    }, mention = "checklist")

    @Test
    fun `report with a missing field`() = assertRefused(mutate(valid) {
        it.array("reports")[0].asJsonObject.remove("simulationNotice")
    }, mention = "simulation notice")

    @Test
    fun `duplicate report ids`() = assertRefused(mutate(valid) { root ->
        root.array("reports").add(root.array("reports")[0])
    }, mention = "more than once")

    @Test
    fun `null entry inside a list`() = assertRefused(mutate(valid) { root ->
        root.array("floors").add(com.google.gson.JsonNull.INSTANCE)
    })

    @Test
    fun `a valid file is still accepted after all of the above`() {
        val target = Fixture()
        assertTrue(target.services.backup.restore(valid, RestoreMode.MERGE) is Outcome.Success)
        assertNull(target.services.backup.inspect(valid).failureOrNull())
    }
}
