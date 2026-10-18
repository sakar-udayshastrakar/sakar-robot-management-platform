package com.sakarrobotics.c40agent.operatorui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.assertCountEquals
import com.sakarrobotics.c40agent.domain.model.Capability
import com.sakarrobotics.c40agent.domain.model.CleaningIntensity
import com.sakarrobotics.c40agent.domain.model.CleaningMode
import com.sakarrobotics.c40agent.domain.model.CleaningRunState
import com.sakarrobotics.c40agent.domain.model.CleaningSession
import com.sakarrobotics.c40agent.operatorui.cleaning.StartCleaningContent
import com.sakarrobotics.c40agent.operatorui.theme.SakarTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Stateless Start Cleaning content with fixed state - nothing here can reach a robot. */
class StartCleaningUiTest {

    @get:Rule
    val compose = createComposeRule()

    private fun session(state: CleaningRunState) = CleaningSession(
        state = state,
        zoneIds = emptyList(),
        mode = CleaningMode.WATER_SUCTION,
        intensity = CleaningIntensity.STANDARD,
        progressPercent = 40,
        elapsedSeconds = 125,
        estimatedRemainingSeconds = 180,
        cyclesRequested = 2,
        cyclesCompleted = 0,
        capability = Capability.SIMULATED
    )

    private class Calls {
        var started: Triple<CleaningMode, CleaningIntensity, Int>? = null
        var pauses = 0
        var resumes = 0
        var stops = 0
    }

    private fun show(session: CleaningSession?, calls: Calls = Calls()) {
        compose.setContent {
            SakarTheme {
                StartCleaningContent(
                    session = session,
                    onBack = {},
                    onStart = { mode, intensity, cycles -> calls.started = Triple(mode, intensity, cycles) },
                    onPause = { calls.pauses++ },
                    onResume = { calls.resumes++ },
                    onStop = { calls.stops++ }
                )
            }
        }
    }

    @Test
    fun the_screen_says_it_is_a_simulation_before_anything_runs() {
        show(session = null)

        compose.onNodeWithText("DEMO / SIMULATED", substring = true).assertIsDisplayed()
        compose.onNodeWithText("no cleaning command is sent to the robot", substring = true).assertIsDisplayed()
        compose.onAllNodesWithText("SIMULATED").assertCountEquals(1)
        compose.onNodeWithText("Start Simulated Clean").assertIsDisplayed()
    }

    @Test
    fun start_passes_the_default_selection() {
        val calls = Calls()
        show(session = null, calls = calls)

        compose.onNodeWithText("Start Simulated Clean").performScrollTo().performClick()

        assertEquals(Triple(CleaningMode.WATER_SUCTION, CleaningIntensity.STANDARD, 1), calls.started)
    }

    @Test
    fun start_passes_the_chosen_mode_intensity_and_cycles() {
        val calls = Calls()
        show(session = null, calls = calls)

        compose.onNodeWithText("SWEEP MOP").performClick()
        compose.onNodeWithText("POWERFUL").performClick()
        compose.onNodeWithText("+").performClick()
        compose.onNodeWithText("Start Simulated Clean").performScrollTo().performClick()

        assertEquals(Triple(CleaningMode.SWEEP_MOP, CleaningIntensity.POWERFUL, 2), calls.started)
    }

    @Test
    fun a_running_session_shows_state_progress_and_a_simulated_badge() {
        show(session = session(CleaningRunState.CLEANING))

        compose.onNodeWithText("Cleaning · WATER SUCTION · STANDARD").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("40% — cycle 1 of 2").assertIsDisplayed()
        compose.onNodeWithText("Elapsed 2:05", substring = true).assertIsDisplayed()
        compose.onNodeWithText("simulated estimate", substring = true).assertIsDisplayed()
        // The page-level badge plus the progress card's own badge.
        compose.onAllNodesWithText("SIMULATED").assertCountEquals(2)
    }

    @Test
    fun pause_and_stop_are_reachable_and_invoke_their_callbacks() {
        val calls = Calls()
        show(session = session(CleaningRunState.CLEANING), calls = calls)

        compose.onNodeWithText("Pause").performScrollTo().performClick()
        compose.onNodeWithText("Stop").performScrollTo().performClick()

        assertEquals(1, calls.pauses)
        assertEquals(1, calls.stops)
    }

    @Test
    fun a_paused_session_offers_resume() {
        val calls = Calls()
        show(session = session(CleaningRunState.PAUSED), calls = calls)

        compose.onNodeWithText("Resume").performScrollTo().performClick()

        assertEquals(1, calls.resumes)
    }
}
