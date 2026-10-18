package com.sakarrobotics.c40agent.operatorui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.assertCountEquals
import com.sakarrobotics.c40agent.domain.model.Capability
import com.sakarrobotics.c40agent.domain.model.MapListResult
import com.sakarrobotics.c40agent.domain.model.MapSummary
import com.sakarrobotics.c40agent.domain.model.RobotLinkState
import com.sakarrobotics.c40agent.operatorui.maps.MapsListContent
import com.sakarrobotics.c40agent.operatorui.maps.TAG_MAPS_LIST
import com.sakarrobotics.c40agent.operatorui.theme.SakarTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Stateless Maps content rendered with fixed state - no AppContainer, SDK or robot. */
class MapsListUiTest {

    @get:Rule
    val compose = createComposeRule()

    private fun show(
        link: RobotLinkState = RobotLinkState.INIT_FAILED,
        result: MapListResult? = null,
        loading: Boolean = false,
        onRefresh: () -> Unit = {},
        onOpenMap: (String) -> Unit = {}
    ) {
        compose.setContent {
            SakarTheme {
                MapsListContent(
                    linkState = link,
                    result = result,
                    loading = loading,
                    lastReadMillis = null,
                    onBack = {},
                    onRefresh = onRefresh,
                    onOpenMap = onOpenMap
                )
            }
        }
    }

    @Test
    fun not_connected_shows_the_reason_and_never_a_live_badge() {
        show(result = MapListResult(emptyList(), "Not connected - the robot SDK is not initialised."))

        compose.onNodeWithText("SDK initialisation failed").assertIsDisplayed()
        // The reason sits below the capability cards in the lazy list.
        compose.onNodeWithTag(TAG_MAPS_LIST)
            .performScrollToNode(hasText("Not connected - the robot SDK is not initialised."))
        compose.onNodeWithText("Not connected - the robot SDK is not initialised.").assertIsDisplayed()
        compose.onAllNodesWithText("LIVE").assertCountEquals(0)
    }

    @Test
    fun unsupported_capabilities_are_labelled_not_available() {
        show(result = MapListResult(emptyList(), "x"))

        compose.onNodeWithText("Floor-plan image preview").assertIsDisplayed()
        compose.onNodeWithText("Create / edit SLAM maps").assertIsDisplayed()
        compose.onNodeWithText("Deploy map to robot").assertIsDisplayed()
        compose.onAllNodesWithText("NOT AVAILABLE").assertCountEquals(3)
    }

    @Test
    fun map_info_is_shown_raw_and_nothing_is_inferred() {
        val map = MapSummary(
            id = "current", name = "Robot map info", inUse = null, sizeBytes = null,
            capability = Capability.UNKNOWN, rawInfo = "<map-info/>"
        )
        show(link = RobotLinkState.CONNECTED, result = MapListResult(listOf(map), null))

        compose.onNodeWithTag(TAG_MAPS_LIST).performScrollToNode(hasText("Robot map info"))
        compose.onNodeWithText("Robot map info").assertIsDisplayed()
        compose.onNodeWithText("In use: unknown  |  Size: unknown").assertIsDisplayed()
        compose.onNodeWithText("<map-info/>").assertIsDisplayed()
    }

    @Test
    fun tapping_a_map_opens_it() {
        var opened: String? = null
        val map = MapSummary("current", "Robot map info", null, null, Capability.UNKNOWN, "raw")
        show(result = MapListResult(listOf(map), null), onOpenMap = { opened = it })

        compose.onNodeWithTag(TAG_MAPS_LIST).performScrollToNode(hasText("Robot map info"))
        compose.onNodeWithText("Robot map info").performClick()

        assertEquals("current", opened)
    }

    @Test
    fun refresh_button_calls_back_and_is_disabled_while_loading() {
        var refreshes = 0
        show(onRefresh = { refreshes++ })
        compose.onNodeWithText("Refresh map info").assertIsEnabled().performClick()
        assertEquals(1, refreshes)
    }

    @Test
    fun refresh_button_is_disabled_while_a_read_is_in_flight() {
        show(loading = true)
        compose.onNodeWithText("Reading...").assertIsNotEnabled()
    }
}
