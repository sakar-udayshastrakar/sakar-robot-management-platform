package com.sakarrobotics.installationassistant

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import com.sakarrobotics.installation.core.AssistantServices
import com.sakarrobotics.installation.core.model.MappingPhase
import com.sakarrobotics.installation.core.storage.InMemoryKeyValueStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * UI tests run against the in-memory SIMULATED backend only - no robot, network or ROS involved.
 */
class AssistantUiTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private lateinit var scope: CoroutineScope
    private lateinit var services: AssistantServices
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun setUp() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        services = AssistantServices(
            store = InMemoryKeyValueStore(),
            scope = scope,
            mappingTickMillis = 100,
            diagnosticsLatencyMillis = 0
        )
        AssistantApp.servicesOverride = services
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After
    fun tearDown() {
        scenario?.close()
        AssistantApp.servicesOverride = null
        scope.cancel()
    }

    private fun tag(t: String) = compose.onNodeWithTag(t, useUnmergedTree = true)

    /** Scrolls the node into view when it sits in a scrollable container; many nodes do not. */
    private fun scrollTo(t: String) {
        try {
            tag(t).performScrollTo()
        } catch (_: AssertionError) {
            // No scrollable ancestor: the node is already laid out in a fixed container.
        }
    }

    private fun click(t: String) {
        scrollTo(t)
        tag(t).performClick()
    }

    /** Asserts the tagged node, or one of its descendants, shows [text] (unmerged semantics tree). */
    private fun assertTagText(t: String, text: String) {
        compose.onNode(
            androidx.compose.ui.test.hasTestTag(t) and (
                androidx.compose.ui.test.hasText(text, substring = true) or
                    androidx.compose.ui.test.hasAnyDescendant(androidx.compose.ui.test.hasText(text, substring = true))
                ),
            useUnmergedTree = true
        ).assertExists()
    }

    private fun waitForTag(t: String, timeoutMs: Long = 8_000) {
        compose.waitUntil(timeoutMs) {
            compose.onAllNodes(androidx.compose.ui.test.hasTestTag(t), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForText(text: String, timeoutMs: Long = 8_000) {
        compose.waitUntil(timeoutMs) {
            compose.onAllNodes(androidx.compose.ui.test.hasText(text, substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun startWizard() {
        click("tile_new_installation")
        waitForText("No installation in progress")
        compose.onNodeWithText("Start new simulated installation", useUnmergedTree = true).performClick()
        waitForTag("wizard_installation_id")
    }

    // ------------------------------------------------------------ navigation

    @Test
    fun home_showsSimulatedBannerAndAllTiles() {
        tag("simulated_banner").assertIsDisplayed()
        listOf(
            "tile_new_installation", "tile_mapping", "tile_diagnostics",
            "tile_floors", "tile_checklist", "tile_report", "tile_settings"
        ).forEach { tag(it).assertIsDisplayed() }
    }

    @Test
    fun everyTileOpensAScreenThatKeepsTheSimulatedBannerAndCanGoBack() {
        val destinations = listOf(
            "tile_mapping" to "Mapping Workspace",
            "tile_diagnostics" to "Sensor Diagnostics",
            "tile_floors" to "Floor",
            "tile_checklist" to "Installation Checklist",
            "tile_report" to "Installation Report",
            "tile_settings" to "Settings & About",
            "tile_new_installation" to "Installation Wizard"
        )
        destinations.forEach { (tile, title) ->
            click(tile)
            waitForText(title)
            tag("simulated_banner").assertIsDisplayed()
            click("back_button")
            waitForTag("tile_new_installation")
        }
    }

    // ---------------------------------------------------------------- wizard

    @Test
    fun wizard_gatesNextUntilStepIsCompleteAndWalksToFinish() {
        startWizard()
        // Overview: Next is blocked until the notice is acknowledged, with a visible reason.
        tag("btn_wizard_next").assertIsNotEnabled()
        tag("step_hint").assertIsDisplayed()
        click("btn_acknowledge")
        tag("btn_wizard_next").assertIsEnabled()
        click("btn_wizard_next")

        // Readiness
        waitForText("System readiness")
        tag("btn_wizard_next").assertIsNotEnabled()
        click("btn_run_readiness")
        waitForTag("diagnostics_overall")
        compose.waitUntil(5_000) { runCatching { tag("btn_wizard_next").assertIsEnabled() }.isSuccess }
        click("btn_wizard_next")

        // Sensors
        waitForText("Sensor diagnostics")
        click("btn_run_sensors")
        waitForTag("diag_row_sensor-lidar")
        compose.waitUntil(5_000) { runCatching { tag("btn_wizard_next").assertIsEnabled() }.isSuccess }
        click("btn_wizard_next")

        // Mapping setup: pick a floor
        waitForText("Floors to map")
        tag("btn_wizard_next").assertIsNotEnabled()
        click("floor_check_Ground floor")
        compose.waitUntil(5_000) { runCatching { tag("btn_wizard_next").assertIsEnabled() }.isSuccess }
        click("btn_wizard_next")

        // Map config: no map for the selected floor yet -> blocked, then create one directly.
        waitForTag("mapconfig_Ground floor")
        tag("btn_wizard_next").assertIsNotEnabled()
        val floorId = services.floors.floors.value.first { it.name == "Ground floor" }.id
        services.mapping.startSession(floorId)
        (services.mapping as com.sakarrobotics.installation.core.data.SimulatedMappingRepository).advance(70)
        services.mapping.finish()
        services.mapping.saveMap("Lobby sweep")
        compose.waitUntil(5_000) { runCatching { tag("btn_wizard_next").assertIsEnabled() }.isSuccess }
        click("btn_wizard_next")

        // Validation
        waitForTag("btn_run_validation")
        click("btn_run_validation")
        waitForTag("validation_result")
        click("btn_wizard_next")

        // Report + finish
        waitForTag("btn_wizard_generate_report")
        tag("btn_wizard_finish").assertIsNotEnabled()
        click("btn_wizard_generate_report")
        waitForTag("wizard_report_generated")
        compose.waitUntil(5_000) { runCatching { tag("btn_wizard_finish").assertIsEnabled() }.isSuccess }
        click("btn_wizard_finish")
        waitForTag("tile_new_installation")
    }

    @Test
    fun wizard_backReturnsToPreviousStep() {
        startWizard()
        waitForTag("btn_acknowledge")
        click("btn_acknowledge")
        click("btn_wizard_next")
        waitForText("System readiness")
        click("btn_wizard_back")
        waitForTag("overview_acknowledged")
    }

    // --------------------------------------------------------------- mapping

    @Test
    fun mapping_startPauseResumeFinishAndSave() {
        click("tile_mapping")
        waitForTag("mapping_sim_notice")
        assertTagText("mapping_sim_notice", "SIMULATED")

        click("btn_start")
        waitForTag("btn_pause")
        click("btn_pause")
        waitForTag("btn_resume")
        assertTagText("mapping_phase", "Paused")
        click("btn_resume")
        waitForTag("btn_pause")

        // Wait for enough simulated coverage, then finish.
        compose.waitUntil(10_000) { services.mapping.session.value.progressPercent >= 35 }
        click("btn_finish")
        waitForTag("btn_save_map")

        // Empty name is rejected with a visible error.
        click("btn_save_map")
        waitForTag("map_name_error")

        scrollTo("map_name_input")
        tag("map_name_input").performTextInput("Test map")
        click("btn_save_map")
        waitForTag("saved_map_row")
        waitForText("Saved simulated map")
        assert(services.mapping.maps.value.any { it.name == "Test map" })
    }

    @Test
    fun mapping_cancelReturnsToIdleWithoutSaving() {
        click("tile_mapping")
        waitForTag("btn_start")
        click("btn_start")
        waitForTag("btn_cancel")
        click("btn_cancel")
        waitForTag("confirm_button") // cancelling a scan asks for confirmation first
        click("confirm_button")
        waitForTag("btn_start")
        assert(services.mapping.session.value.phase == MappingPhase.CANCELLED)
        assert(services.mapping.maps.value.isEmpty())
    }

    // ---------------------------------------------------------------- floors

    @Test
    fun floors_addRenameAndDeleteWithConfirmation() {
        click("tile_floors")
        waitForTag("btn_add_floor")

        click("btn_add_floor")
        tag("name_input").performTextInput("Mezzanine")
        click("confirm_button")
        waitForTag("floor_card_Mezzanine")

        click("rename_floor_Mezzanine")
        tag("name_input").performTextReplacement("Mezzanine B")
        click("confirm_button")
        waitForTag("floor_card_Mezzanine B")

        // Delete asks for confirmation; cancelling keeps the floor.
        click("delete_floor_Mezzanine B")
        waitForTag("cancel_button")
        click("cancel_button")
        tag("floor_card_Mezzanine B").assertIsDisplayed()

        click("delete_floor_Mezzanine B")
        waitForTag("confirm_button")
        click("confirm_button")
        compose.waitUntil(5_000) {
            compose.onAllNodes(androidx.compose.ui.test.hasTestTag("floor_card_Mezzanine B"), useUnmergedTree = true)
                .fetchSemanticsNodes().isEmpty()
        }
    }

    @Test
    fun floors_duplicateNameIsRejectedWithVisibleError() {
        click("tile_floors")
        waitForTag("btn_add_floor")
        click("btn_add_floor")
        tag("name_input").performTextInput("Ground floor")
        click("confirm_button")
        waitForTag("name_error")
    }

    // ---------------------------------------------------------------- report

    @Test
    fun report_generateShowsSimulationOnlyContent() {
        click("tile_report")
        waitForTag("report_sim_notice")
        click("btn_generate_report")
        waitForTag("report_view")
        assertTagText("report_notice", "SIMULATION ONLY")
        tag("report_mapping_status").assertIsDisplayed()
        waitForTag("btn_copy_report")
    }

    @Test
    fun report_deleteRequiresConfirmation() {
        click("tile_report")
        waitForTag("btn_generate_report")
        click("btn_generate_report")
        waitForTag("btn_delete_report")
        click("btn_delete_report")
        waitForTag("confirm_button")
        click("confirm_button")
        waitForText("No reports yet")
    }

    // ----------------------------------------------------------- diagnostics

    @Test
    fun diagnostics_runShowsSimulatedRowsForEverySensor() {
        click("tile_diagnostics")
        waitForTag("diagnostics_sim_notice")
        click("btn_run_diagnostics")
        waitForTag("diagnostics_overall")
        listOf("lidar", "depth", "ultrasonic", "imu", "localization", "mapping").map { "sensor-$it" }.forEach {
            waitForTag("diag_row_$it")
        }
    }

    // ------------------------------------------------------------- checklist

    @Test
    fun checklist_blockedRequiresNoteThenSaves() {
        click("tile_checklist")
        waitForTag("checklist_list")
        val id = services.installations.ensureCurrent().checklist.first().id
        click("chip_${id}_BLOCKED")
        click("save_$id")
        waitForTag("error_$id")
        scrollTo("note_$id")
        tag("note_$id").performTextInput("Door too narrow")
        click("save_$id")
        compose.waitUntil(5_000) {
            compose.onAllNodes(androidx.compose.ui.test.hasTestTag("error_$id"), useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
        }
        assertTagText("saved_state_$id", "Blocked")
    }
}
