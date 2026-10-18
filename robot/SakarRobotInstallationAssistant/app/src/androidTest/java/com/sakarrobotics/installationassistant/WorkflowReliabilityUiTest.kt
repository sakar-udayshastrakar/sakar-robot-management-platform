package com.sakarrobotics.installationassistant

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import com.sakarrobotics.installation.core.AssistantServices
import com.sakarrobotics.installation.core.model.MappingPhase
import com.sakarrobotics.installation.core.storage.InMemoryKeyValueStore
import com.sakarrobotics.installation.core.storage.KeyValueStore
import com.sakarrobotics.installation.core.storage.StorageException
import com.sakarrobotics.installationassistant.data.FileKeyValueStore
import java.io.File
import kotlinx.coroutines.CoroutineScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val GROUND = "floor-ground"

/** Wizard progress must survive both a configuration change and a full app restart. */
class WorkflowRestartUiTest : UiTestBase() {

    private lateinit var dataDir: File

    override fun createServices(scope: CoroutineScope): AssistantServices {
        dataDir = File(context.cacheDir, "restart-test-data").apply { deleteRecursively(); mkdirs() }
        return servicesOn(dataDir, scope)
    }

    private fun servicesOn(dir: File, scope: CoroutineScope) = AssistantServices(
        store = FileKeyValueStore(dir), scope = scope, mappingTickMillis = 100, diagnosticsLatencyMillis = 0
    )

    @org.junit.After
    fun cleanData() {
        dataDir.deleteRecursively()
    }

    @Test
    fun wizardProgressSurvivesAConfigurationChange() {
        startWizardFromHome()
        click("btn_acknowledge")
        click("btn_wizard_next")
        waitForTagText("step_title", "System readiness")

        scenario!!.recreate()

        waitForTagText("step_title", "System readiness")
        assertTrue(services.installations.current.value!!.overviewAcknowledged)
    }

    @Test
    fun wizardResumesAtTheFirstIncompleteStepAfterAFullAppRestart() {
        startWizardFromHome()
        val id = services.installations.current.value!!.id
        click("btn_acknowledge")
        click("btn_wizard_next")
        click("btn_run_readiness")
        waitForTag("diagnostics_overall")
        services.floors.addFloor("Mezzanine")

        // Kill the activity and rebuild every service from what is on disk, as after a process restart.
        scenario!!.close()
        services.close()
        val restarted = servicesOn(dataDir, scope)
        AssistantApp.servicesOverride = restarted
        services = restarted
        scenario = androidx.test.core.app.ActivityScenario.launch(MainActivity::class.java)

        waitForTag("tile_new_installation")
        assertTagText("tile_new_installation", "Resume $id")
        click("tile_new_installation")
        // Overview and readiness are done, so the wizard reopens on Sensor diagnostics.
        waitForTagText("step_title", "Sensor diagnostics")
        assertEquals(id, restarted.installations.current.value!!.id)
        assertTrue(restarted.installations.current.value!!.readiness != null)
        assertTrue(restarted.floors.floors.value.any { it.name == "Mezzanine" })
    }

    @Test
    fun savedMapsAndChecklistNotesSurviveAFullAppRestart() {
        services.mapping.startSession(GROUND)
        (services.mapping as com.sakarrobotics.installation.core.data.SimulatedMappingRepository).advance(200)
        services.mapping.saveMap("Lobby")
        services.installations.startNew()
        val itemId = services.installations.current.value!!.checklist.first().id
        services.installations.updateChecklist(itemId, com.sakarrobotics.installation.core.model.ChecklistState.BLOCKED, "Door too narrow")

        scenario!!.close()
        services.close()
        val restarted = servicesOn(dataDir, scope)
        AssistantApp.servicesOverride = restarted
        services = restarted
        scenario = androidx.test.core.app.ActivityScenario.launch(MainActivity::class.java)

        click("tile_checklist")
        waitForTag("checklist_$itemId")
        assertTagText("saved_state_$itemId", "Blocked")
        assertEquals("Lobby", restarted.mapping.maps.value.single().name)
    }
}

/** A start-over / back navigation / session interruption must always be predictable. */
class WorkflowControlUiTest : UiTestBase() {

    @Test
    fun startOverAsksForConfirmationAndKeepsTheInstallationWhenCancelled() {
        startWizardFromHome()
        val id = services.installations.current.value!!.id

        click("btn_start_over")
        waitForText("Start a new installation?")
        click("cancel_button")
        waitUntilGone("confirm_button")
        assertEquals(id, services.installations.current.value!!.id)

        click("btn_start_over")
        waitForTag("confirm_button")
        click("confirm_button")
        compose.waitUntil(5_000) { services.installations.current.value!!.id != id }
        assertNotEquals(id, services.installations.current.value!!.id)
    }

    @Test
    fun systemBackFromTheWizardReturnsHomeWithoutLosingProgress() {
        startWizardFromHome()
        click("btn_acknowledge")
        scenario!!.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        waitForTag("tile_new_installation")
        assertTrue(services.installations.current.value!!.overviewAcknowledged)
        assertTagText("tile_new_installation", "Resume")
    }

    @Test
    fun cancellingARunningSimulationNeedsConfirmation() {
        click("tile_mapping")
        waitForTag("btn_start")
        click("btn_start")
        waitForTag("btn_cancel")

        click("btn_cancel")
        waitForText("Cancel the simulated session?")
        click("cancel_button")                       // "Cancel" in the dialog keeps the session
        waitUntilGone("confirm_button")
        assertTrue(services.mapping.session.value.isActive)

        click("btn_cancel")
        waitForTag("confirm_button")
        click("confirm_button")
        compose.waitUntil(5_000) { services.mapping.session.value.phase == MappingPhase.CANCELLED }
        assertTrue(services.mapping.maps.value.isEmpty())
    }

    @Test
    fun discardingAFinishedUnsavedScanNeedsConfirmation() {
        click("tile_mapping")
        waitForTag("btn_start")
        click("btn_start")
        compose.waitUntil(10_000) { services.mapping.session.value.progressPercent >= 35 }
        click("btn_finish")
        waitForTag("btn_discard")

        click("btn_discard")
        waitForText("Discard the finished scan?")
        click("cancel_button")
        waitUntilGone("confirm_button")
        assertEquals(MappingPhase.FINISHED, services.mapping.session.value.phase)

        click("btn_start")                            // "Start again" would replace the unsaved scan
        waitForText("Start a new scan?")
        click("cancel_button")
        waitUntilGone("confirm_button")
        assertEquals(MappingPhase.FINISHED, services.mapping.session.value.phase)
    }

    @Test
    fun leavingTheMappingWorkspacePausesTheSimulationInsteadOfLeavingItRunning() {
        click("tile_mapping")
        waitForTag("btn_start")
        click("btn_start")
        compose.waitUntil(8_000) { services.mapping.session.value.progressPercent >= 5 }

        click("back_button")
        waitForTag("tile_mapping")
        compose.waitUntil(5_000) { services.mapping.session.value.phase == MappingPhase.PAUSED }
        val frozen = services.mapping.session.value.progressPercent
        Thread.sleep(800) // several simulation ticks would have passed
        assertEquals("no background ticking after leaving the screen", frozen, services.mapping.session.value.progressPercent)

        click("tile_mapping")
        waitForTag("btn_resume")
        assertTagText("mapping_message", "Paused because")
        click("btn_resume")
        compose.waitUntil(8_000) { services.mapping.session.value.progressPercent > frozen }
        click("btn_pause")
    }

    @Test
    fun theAppGoingToTheBackgroundPausesARunningSimulation() {
        click("tile_mapping")
        waitForTag("btn_start")
        click("btn_start")
        compose.waitUntil(8_000) { services.mapping.session.value.progressPercent >= 5 }

        scenario!!.moveToState(androidx.lifecycle.Lifecycle.State.CREATED) // activity stopped
        eventually(5_000) { services.mapping.session.value.phase == MappingPhase.PAUSED }
        val frozen = services.mapping.session.value.progressPercent
        Thread.sleep(600)
        assertEquals(frozen, services.mapping.session.value.progressPercent)
        scenario!!.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
    }
}

/** A failing disk is surfaced to the user instead of silently losing changes. */
class PersistenceErrorUiTest : UiTestBase() {

    private class FailingStore : KeyValueStore {
        private val inner = InMemoryKeyValueStore()
        override fun get(key: String) = inner.get(key)
        override fun put(key: String, value: String) { throw StorageException("disk full") }
        override fun remove(key: String) { throw StorageException("disk full") }
    }

    override fun createServices(scope: CoroutineScope) = AssistantServices(
        store = FailingStore(), scope = scope, mappingTickMillis = 100, diagnosticsLatencyMillis = 0
    )

    @Test
    fun aFailedSaveShowsADismissibleWarningOnEveryScreen() {
        services.floors.addFloor("Mezzanine")
        waitForTag("persistence_error")
        assertTagText("persistence_error", "may be lost")
        assertTagText("persistence_error", "disk full")

        click("tile_floors")
        waitForTag("persistence_error")
        assertTrue(services.floors.floors.value.any { it.name == "Mezzanine" })

        click("back_button")
        waitForTag("tile_floors")
        services.persistence.dismiss()
        waitUntilGone("persistence_error")
    }
}

/** PDF export from the Report screen: success, share failure, and the system share sheet itself. */
class ReportPdfUiTest : UiTestBase() {

    private fun openReportAndGenerate() {
        services.installations.startNew()
        services.installations.setSelectedFloors(listOf(GROUND))
        click("tile_report")
        waitForTag("btn_generate_report")
        click("btn_generate_report")
        waitForTag("btn_export_pdf")
    }

    @Test
    fun exportCreatesARealPdfAndHandsItToTheShareSheet() {
        var shared: File? = null
        AssistantApp.shareOverride = { _, file ->
            shared = file
            Result.success(Unit)
        }
        openReportAndGenerate()
        click("btn_export_pdf")
        waitForTagText("report_feedback", "PDF created")

        val pdf = shared!!
        assertTrue(pdf.exists())
        assertTrue(pdf.name.startsWith("installation-report-INST-") || pdf.name.startsWith("installation-report-"))
        assertTrue(pdf.name.endsWith(".pdf"))
        assertTrue("PDF has content", pdf.length() > 1_500)
        assertEquals("%PDF-", pdf.inputStream().use { String(it.readNBytesCompat(5)) })
        assertEquals("reports", pdf.parentFile!!.name)
        assertTrue("created in the app's private cache", pdf.absolutePath.startsWith(context.cacheDir.absolutePath))
    }

    @Test
    fun aShareFailureIsReportedAndTheAppKeepsWorking() {
        AssistantApp.shareOverride = { _, _ -> Result.failure(IllegalStateException("No app on this tablet can receive a PDF to share.")) }
        openReportAndGenerate()
        click("btn_export_pdf")
        waitForTagText("report_feedback", "could not be shared")
        assertTagText("report_feedback", "No app on this tablet")
        tag("btn_export_pdf").assertIsEnabled()
        tag("report_view").assertIsDisplayed()
    }

    @Test
    fun exportIsAvailableAgainAfterAnError() {
        var calls = 0
        AssistantApp.shareOverride = { _, _ ->
            calls++
            if (calls == 1) Result.failure(IllegalStateException("boom")) else Result.success(Unit)
        }
        openReportAndGenerate()
        click("btn_export_pdf")
        waitForTagText("report_feedback", "could not be shared")
        click("btn_export_pdf")
        waitForTagText("report_feedback", "PDF created")
        assertEquals(2, calls)
    }

    private fun foregroundPackage(): String? = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        .uiAutomation.rootInActiveWindow?.packageName?.toString()

    @Test
    fun theRealShareSheetOpensForTheGeneratedPdf() {
        openReportAndGenerate()
        click("btn_export_pdf")
        // No override: the production path builds a FileProvider URI and opens the system chooser, which runs
        // in the "android" package and covers this app.
        try {
            eventually(10_000) { foregroundPackage() == "android" }
        } finally {
            // Always dismiss the chooser so it can never leak into the next test.
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
                .performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
        }
        eventually(10_000) { foregroundPackage() == context.packageName }
        waitForTagText("report_feedback", "PDF created")
    }

    private fun java.io.InputStream.readNBytesCompat(n: Int): ByteArray {
        val buffer = ByteArray(n)
        var read = 0
        while (read < n) {
            val r = read(buffer, read, n - read)
            if (r < 0) break
            read += r
        }
        return buffer.copyOf(read)
    }
}
