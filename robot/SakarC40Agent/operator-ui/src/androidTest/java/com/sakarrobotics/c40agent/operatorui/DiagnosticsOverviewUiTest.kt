package com.sakarrobotics.c40agent.operatorui

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.sakarrobotics.c40agent.domain.model.BatteryState
import com.sakarrobotics.c40agent.domain.model.Capability
import com.sakarrobotics.c40agent.domain.model.CloudSyncState
import com.sakarrobotics.c40agent.domain.model.LogLevel
import com.sakarrobotics.c40agent.domain.model.LogRecord
import com.sakarrobotics.c40agent.domain.model.LogSource
import com.sakarrobotics.c40agent.domain.model.Rated
import com.sakarrobotics.c40agent.domain.model.RobotLinkState
import com.sakarrobotics.c40agent.domain.model.SensorReadings
import com.sakarrobotics.c40agent.operatorui.diagnostics.DiagnosticsOverviewContent
import com.sakarrobotics.c40agent.operatorui.diagnostics.DiagnosticsOverviewState
import com.sakarrobotics.c40agent.operatorui.diagnostics.TAG_CONNECTION
import com.sakarrobotics.c40agent.operatorui.diagnostics.TAG_LIST
import com.sakarrobotics.c40agent.operatorui.diagnostics.TAG_SENSORS
import com.sakarrobotics.c40agent.operatorui.theme.SakarTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Renders the stateless Diagnostics content with fixed state. No AppContainer, SDK or robot is
 * involved: these tests only check that the screen labels what it is shown honestly.
 */
class DiagnosticsOverviewUiTest {

    @get:Rule
    val compose = createComposeRule()

    private val unknownBattery = BatteryState(null, null, null, null, Capability.UNKNOWN)

    private fun state(
        link: RobotLinkState = RobotLinkState.DISCONNECTED,
        lastComm: Long? = null,
        battery: BatteryState = unknownBattery,
        sensors: SensorReadings? = null,
        events: List<LogRecord> = emptyList()
    ) = DiagnosticsOverviewState(
        link = link,
        lastCommunicationMillis = lastComm,
        cloud = CloudSyncState.OFFLINE,
        battery = battery,
        snapshot = null,
        sensors = sensors,
        recentEvents = events,
        busy = null,
        nowMillis = 100_000L
    )

    private fun show(state: DiagnosticsOverviewState, onReadSensors: () -> Unit = {}, onCopy: () -> Unit = {}) {
        compose.setContent {
            SakarTheme {
                DiagnosticsOverviewContent(
                    state = state,
                    onBack = {},
                    onReadBattery = {},
                    onReadRuntime = {},
                    onReadSensors = onReadSensors,
                    onRefreshEvents = {},
                    onCopyReport = onCopy
                )
            }
        }
    }

    private fun connectionHasNoLiveBadge() =
        compose.onNodeWithTag(TAG_CONNECTION).assert(hasAnyDescendant(hasText("LIVE")).not())

    @Test
    fun offline_state_is_a_state_chip_and_never_a_live_badge() {
        show(state(link = RobotLinkState.INIT_FAILED))

        compose.onNodeWithText("STATE: OFFLINE").assertIsDisplayed()
        compose.onNodeWithText("SDK initialisation failed").assertIsDisplayed()
        connectionHasNoLiveBadge()
    }

    @Test
    fun connected_link_without_any_exchange_reads_unknown_not_live() {
        show(state(link = RobotLinkState.CONNECTED, lastComm = null))

        compose.onNodeWithText("STATE: UNKNOWN").assertIsDisplayed()
        connectionHasNoLiveBadge()
    }

    @Test
    fun a_past_exchange_while_offline_is_not_badged_live() {
        show(state(link = RobotLinkState.DISCONNECTED, lastComm = 40_000L))

        compose.onNodeWithText("STATE: OFFLINE").assertIsDisplayed()
        compose.onNodeWithText("1m ago").assertIsDisplayed()
        connectionHasNoLiveBadge()
    }

    @Test
    fun online_state_may_show_the_live_badge_for_the_last_exchange() {
        show(state(link = RobotLinkState.CONNECTED, lastComm = 99_000L))

        compose.onNodeWithText("STATE: ONLINE").assertIsDisplayed()
        compose.onNodeWithTag(TAG_CONNECTION).assert(hasAnyDescendant(hasText("LIVE")))
    }

    @Test
    fun battery_is_never_shown_as_a_percentage_without_a_verified_reading() {
        show(state(battery = BatteryState(null, null, null, "{\"x\":1}", Capability.UNKNOWN)))

        compose.onNodeWithText("Raw response (not decoded): {\"x\":1}").assertIsDisplayed()
        compose.onAllNodesWithText("%", substring = true).assertCountEquals(0)
    }

    @Test
    fun failed_sensor_reads_are_not_available_never_live() {
        val failed = Rated("error: SDK error -1004: Blocked", Capability.UNAVAILABLE)
        show(state(sensors = SensorReadings(failed, failed, failed, failed)))

        compose.onNodeWithTag(TAG_LIST).performScrollToNode(hasTestTag(TAG_SENSORS))
        compose.onNodeWithTag(TAG_SENSORS).assert(hasAnyDescendant(hasText("LIVE")).not())
        compose.onAllNodes(hasText("NOT AVAILABLE") and hasAnyAncestor(hasTestTag(TAG_SENSORS))).assertCountEquals(4)
    }

    @Test
    fun a_successful_sensor_read_is_live_and_verbatim() {
        val ok = Rated("{\"range\":[1,2,3]}", Capability.REAL)
        val failed = Rated("error: x", Capability.UNAVAILABLE)
        show(state(sensors = SensorReadings(ok, failed, failed, failed)))

        compose.onNodeWithTag(TAG_LIST).performScrollToNode(hasTestTag(TAG_SENSORS))
        compose.onNodeWithTag(TAG_SENSORS).assert(hasAnyDescendant(hasText("LIVE")))
        compose.onNodeWithText("{\"range\":[1,2,3]}").assertIsDisplayed()
    }

    @Test
    fun read_sensors_button_invokes_the_callback() {
        var clicks = 0
        show(state(), onReadSensors = { clicks++ })

        compose.onNodeWithTag(TAG_LIST).performScrollToNode(hasTestTag(TAG_SENSORS))
        compose.onNodeWithText("Read sensors").performClick()

        assertEquals(1, clicks)
    }

    @Test
    fun recent_events_are_listed_with_level_and_tag() {
        show(
            state(
                events = listOf(
                    LogRecord(99_000L, LogSource.APPLICATION, LogLevel.ERROR, "Connection", "SDK initialisation failed (error 203002)")
                )
            )
        )

        compose.onNodeWithTag(TAG_LIST).performScrollToNode(hasText("Copy support report"))
        compose.onNodeWithText("ERROR", substring = true).assertExists()
        compose.onNodeWithText("SDK initialisation failed (error 203002)").assertExists()
    }

    @Test
    fun support_report_button_invokes_the_callback() {
        var copies = 0
        show(state(), onCopy = { copies++ })

        compose.onNodeWithTag(TAG_LIST).performScrollToNode(hasText("Copy support report"))
        compose.onNodeWithText("Copy support report").performClick()

        assertEquals(1, copies)
    }
}
