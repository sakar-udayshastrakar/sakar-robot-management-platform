package com.sakarrobotics.installationassistant

import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import com.sakarrobotics.installation.core.AssistantServices
import com.sakarrobotics.installation.core.backup.BackupFormat
import com.sakarrobotics.installation.core.data.SimulatedMappingRepository
import com.sakarrobotics.installation.core.model.MappingPhase
import com.sakarrobotics.installation.core.model.Outcome
import com.sakarrobotics.installation.core.storage.InMemoryKeyValueStore
import com.google.gson.JsonParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val GROUND = "floor-ground"

/** Saves a complete simulated map straight through the repository (no timer involved). */
private fun AssistantServices.addMap(floorId: String, name: String) {
    check(mapping.startSession(floorId).isSuccess)
    (mapping as SimulatedMappingRepository).advance(200)
    check(mapping.saveMap(name).isSuccess) { "could not save $name" }
}

/** An unrelated installation, standing in for data made on another tablet. */
private fun otherTablet(): AssistantServices = AssistantServices(
    store = InMemoryKeyValueStore(),
    scope = CoroutineScope(Job()),
    mappingTickMillis = 0,
    diagnosticsLatencyMillis = 0
)

class SettingsAndBackupUiTest : UiTestBase() {

    private fun openSettings() {
        click("tile_settings")
        waitForTag("about_card")
    }

    private fun fileUri(name: String, text: String? = null): Uri {
        val file = workFile(name)
        if (text != null) file.writeText(text)
        return Uri.fromFile(file)
    }

    // -------------------------------------------------------------- about

    @Test
    fun about_showsNameVersionModeAndTheNotYetEnabledNotice() {
        openSettings()
        tag("simulated_banner").assertIsDisplayed()
        assertTagText("about_name", "Robot Installation Assistant")
        assertTagText("about_version", AssistantApp.versionName(context))
        assertTagText("about_version", "0.2.0-sim")
        assertTagText("about_mode", "SIMULATED")
        assertTagText("about_notice", "not yet enabled")
        assertTagText("about_notice", "cannot connect to a robot")
    }

    // -------------------------------------------------------------- reset

    @Test
    fun reset_cancelKeepsDataAndConfirmDeletesIt() {
        services.floors.addFloor("Roof")
        services.addMap(GROUND, "Lobby")
        services.installations.startNew()
        services.generateReport()
        openSettings()

        click("btn_reset_data")
        waitForText("Delete all demo data?")
        waitForText("every simulated map")
        click("cancel_button")
        waitUntilGone("confirm_button")
        assertEquals("nothing is deleted on cancel", 3, services.floors.floors.value.size)
        assertEquals(1, services.mapping.maps.value.size)

        click("btn_reset_data")
        waitForTag("confirm_button")
        click("confirm_button")
        waitForTagText("settings_feedback", "All demo data was deleted")
        assertEquals(listOf("Ground floor", "First floor"), services.floors.floors.value.map { it.name })
        assertTrue(services.mapping.maps.value.isEmpty())
        assertNull(services.installations.current.value)
        assertTrue(services.reports.reports.value.isEmpty())
    }

    // -------------------------------------------------------------- export

    @Test
    fun exportBackup_writesAValidVersionedSimulationOnlyFile() {
        services.addMap(GROUND, "Lobby")
        services.installations.startNew()
        val target = fileUri("backup.json")
        pickerResults += target
        openSettings()

        click("btn_export_backup")
        waitForTagText("settings_feedback", "Backup saved")

        val text = workFile("backup.json").readText()
        val root = JsonParser.parseString(text).asJsonObject
        assertEquals(BackupFormat.FORMAT_ID, root["format"].asString)
        assertEquals(BackupFormat.VERSION, root["formatVersion"].asInt)
        assertEquals("0.2.0-sim", root["appVersion"].asString)
        assertTrue(root["notice"].asString.contains("not compatible with Keenon"))
        assertTrue(services.backup.inspect(text) is Outcome.Success)
        assertEquals(1, root["data"].asJsonObject["maps"].asJsonArray.size())
        assertEquals(listOf("CreateDocument"), picker.launches)
    }

    @Test
    fun exportBackup_cancellingThePickerChangesNothingAndShowsNoError() {
        openSettings()
        click("btn_export_backup")           // picker returns null = cancelled
        compose.waitForIdle()
        assertFalse(exists("settings_feedback"))
        assertFalse(workFile("backup.json").exists())
    }

    @Test
    fun exportBackup_unwritableLocationShowsAnUnderstandableError() {
        pickerResults += Uri.fromFile(java.io.File(workDir, "no-such-folder/backup.json"))
        openSettings()
        click("btn_export_backup")
        waitForTagText("settings_feedback", "could not be saved")
    }

    // -------------------------------------------------------------- restore: refused files

    private fun assertRestoreRefused(fileText: String, mention: String) {
        val before = listOf(services.floors.floors.value, services.mapping.maps.value, services.installations.current.value, services.reports.reports.value)
        pickerResults += fileUri("bad.json", fileText)
        openSettings()

        click("btn_restore_backup")
        waitForTagText("settings_feedback", "Nothing was changed")
        assertTagText("settings_feedback", mention)
        assertFalse("no restore dialog for a refused file", exists("restore_dialog"))
        assertEquals(before, listOf(services.floors.floors.value, services.mapping.maps.value, services.installations.current.value, services.reports.reports.value))
    }

    @Test
    fun restore_malformedFileIsRefusedWithAClearMessage() {
        services.addMap(GROUND, "Keep me")
        assertRestoreRefused("this is definitely { not json", "not valid JSON")
    }

    @Test
    fun restore_truncatedBackupIsRefused() {
        services.addMap(GROUND, "Keep me")
        val full = otherTablet().also { it.addMap(GROUND, "Other") }.backup.export("x")
        assertRestoreRefused(full.substring(0, full.length / 2), "not valid JSON")
    }

    @Test
    fun restore_backupFromANewerVersionIsRefusedAsIncompatible() {
        val newer = otherTablet().backup.export("9.9").replace("\"formatVersion\": 1", "\"formatVersion\": 99")
        assertRestoreRefused(newer, "newer version")
    }

    @Test
    fun restore_fileThatIsNotABackupIsRefused() {
        assertRestoreRefused("""{"some":"other app's file"}""", "not a Sakar")
    }

    @Test
    fun restore_binaryFileIsRefusedAsNotText() {
        val file = workFile("binary.json")
        file.writeBytes(byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0x00, 0x80.toByte(), 0xC3.toByte(), 0x28))
        pickerResults += Uri.fromFile(file)
        openSettings()
        click("btn_restore_backup")
        waitForTagText("settings_feedback", "Nothing was changed")
        assertTagText("settings_feedback", "not a text file")
    }

    @Test
    fun restore_missingFileIsRefused() {
        pickerResults += Uri.fromFile(java.io.File(workDir, "gone.json"))
        openSettings()
        click("btn_restore_backup")
        waitForTagText("settings_feedback", "Nothing was changed")
    }

    // -------------------------------------------------------------- restore: valid files

    @Test
    fun restore_mergeAddsMissingDataKeepsExistingAndNeverDuplicates() {
        services.addMap(GROUND, "Existing map")
        val other = otherTablet().also { it.addMap(GROUND, "Imported map") }
        pickerResults += fileUri("good.json", other.backup.export("0.2.0-sim"))
        pickerResults += fileUri("good.json")
        openSettings()

        click("btn_restore_backup")
        waitForTag("restore_dialog")
        assertTagText("restore_counts", "1 simulated map(s)")
        click("btn_restore_merge")
        waitForTagText("settings_feedback", "Merged the backup")
        assertEquals(setOf("Existing map", "Imported map"), services.mapping.maps.value.map { it.name }.toSet())
        assertEquals(2, services.floors.floors.value.size)

        // Restoring the same file again must not create duplicates.
        click("btn_restore_backup")
        waitForTag("restore_dialog")
        click("btn_restore_merge")
        waitForTagText("settings_feedback", "Maps added: 0")
        assertEquals(2, services.mapping.maps.value.size)
    }

    @Test
    fun restore_cancelFromTheDialogChangesNothing() {
        val other = otherTablet().also { it.addMap(GROUND, "Imported map") }
        pickerResults += fileUri("good.json", other.backup.export("0.2.0-sim"))
        openSettings()
        click("btn_restore_backup")
        waitForTag("restore_dialog")
        click("btn_restore_cancel")
        waitUntilGone("restore_dialog")
        assertTrue(services.mapping.maps.value.isEmpty())
    }

    @Test
    fun restore_replaceAsksForASecondConfirmationBeforeDeletingAnything() {
        services.addMap(GROUND, "Will be replaced")
        val other = otherTablet().also { it.addMap(GROUND, "Imported map") }
        pickerResults += fileUri("good.json", other.backup.export("0.2.0-sim"))
        openSettings()
        click("btn_restore_backup")
        waitForTag("restore_dialog")

        click("btn_restore_replace")
        waitForText("Replace all current data?")
        click("cancel_button")
        waitUntilGone("confirm_button")
        assertEquals("still untouched after cancelling the confirmation", listOf("Will be replaced"), services.mapping.maps.value.map { it.name })

        click("btn_restore_replace")
        waitForTag("confirm_button")
        click("confirm_button")
        waitForTagText("settings_feedback", "Replaced all data")
        assertEquals(listOf("Imported map"), services.mapping.maps.value.map { it.name })
    }

    @Test
    fun restore_replaceStopsARunningSimulatedSession() {
        services.mapping.startSession(GROUND)
        val other = otherTablet().also { it.addMap(GROUND, "Imported map") }
        pickerResults += fileUri("good.json", other.backup.export("0.2.0-sim"))
        openSettings()
        click("btn_restore_backup")
        waitForTag("restore_dialog")
        click("btn_restore_replace")
        waitForTag("confirm_button")
        click("confirm_button")
        waitForTagText("settings_feedback", "Replaced all data")
        assertFalse(services.mapping.session.value.isActive)
        assertEquals(MappingPhase.IDLE, services.mapping.session.value.phase)
    }

    // -------------------------------------------------------------- input validation in dialogs

    @Test
    fun floors_blankAndOverlongNamesShowAnErrorAndKeepTheDialogOpen() {
        click("tile_floors")
        waitForTag("btn_add_floor")
        click("btn_add_floor")
        click("confirm_button")
        waitForTagText("name_error", "Enter a floor name")
        tag("name_input").performTextInput("x".repeat(41))
        click("confirm_button")
        waitForTagText("name_error", "40 characters")
        assertEquals(2, services.floors.floors.value.size)
        click("cancel_button")
    }

    @Test
    fun floors_duplicateWithDifferentCaseAndSpacingIsRejected() {
        click("tile_floors")
        waitForTag("btn_add_floor")
        click("btn_add_floor")
        tag("name_input").performTextInput("  FIRST    floor ")
        click("confirm_button")
        waitForTagText("name_error", "already exists")
        assertEquals(2, services.floors.floors.value.size)
    }
}

/** Deleting saved data is always confirmed, and a floor that still owns maps cannot be deleted at all. */
class DestructiveActionsUiTest : UiTestBase() {

    @Test
    fun deletingASavedMapNeedsConfirmationAndCancelKeepsIt() {
        services.addMap(GROUND, "Lobby")
        click("tile_floors")
        waitForTag("delete_map_Lobby")

        click("delete_map_Lobby")
        waitForTag("confirm_button")
        click("cancel_button")
        waitUntilGone("confirm_button")
        assertEquals(1, services.mapping.maps.value.size)

        click("delete_map_Lobby")
        waitForTag("confirm_button")
        click("confirm_button")
        compose.waitUntil(5_000) { services.mapping.maps.value.isEmpty() }
    }

    @Test
    fun aFloorThatStillHasMapsCannotBeDeletedAndTheUserIsToldWhy() {
        services.addMap(GROUND, "Lobby")
        click("tile_floors")
        waitForTag("delete_floor_Ground floor")

        click("delete_floor_Ground floor")
        waitForTag("confirm_button")
        click("confirm_button")
        waitForTagText("floors_feedback", "still has 1 saved simulated map")
        assertEquals(2, services.floors.floors.value.size)
    }

    @Test
    fun renamingAMapRejectsADuplicateNameOnTheSameFloor() {
        services.addMap(GROUND, "Lobby")
        services.addMap(GROUND, "Corridor")
        click("tile_floors")
        waitForTag("rename_map_Corridor")
        click("rename_map_Corridor")
        tag("name_input").performTextReplacement("  LOBBY ")
        click("confirm_button")
        waitForTagText("name_error", "already exists")
        assertTrue(services.mapping.maps.value.any { it.name == "Corridor" })
    }

    @Test
    fun deletingTheCurrentReportNeedsConfirmationAndCancelKeepsIt() {
        services.installations.startNew()
        services.generateReport()
        click("tile_report")
        waitForTag("btn_delete_report")
        click("btn_delete_report")
        waitForTag("confirm_button")
        click("cancel_button")
        waitUntilGone("confirm_button")
        assertEquals(1, services.reports.reports.value.size)
    }
}
