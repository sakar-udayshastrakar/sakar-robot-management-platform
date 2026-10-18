package com.sakarrobotics.c40agent.data.repository

import com.sakarrobotics.c40agent.domain.model.RobotLinkState
import com.sakarrobotics.c40agent.robot.C40RobotController
import com.sakarrobotics.c40agent.telemetry.ConnectionStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

/** The controller is mocked: no Peanut SDK is initialised and nothing talks to a robot. */
class RobotConnectionRepositoryImplTest {

    @Test
    fun `link state follows the controller status instead of going stale`() = runTest {
        val controller = mock(C40RobotController::class.java)
        // Successive reads: the status changes outside connect()/disconnect().
        `when`(controller.status).thenReturn(
            ConnectionStatus.DISCONNECTED, ConnectionStatus.CONNECTED, ConnectionStatus.DISCONNECTED
        )
        val repo = RobotConnectionRepositoryImpl(controller, pollIntervalMillis = 10)

        val seen = repo.linkState.take(3).toList()

        assertEquals(
            listOf(RobotLinkState.DISCONNECTED, RobotLinkState.CONNECTED, RobotLinkState.DISCONNECTED),
            seen
        )
    }

    @Test
    fun `repeated identical statuses are not re-emitted`() = runTest {
        val controller = mock(C40RobotController::class.java)
        `when`(controller.status).thenReturn(
            ConnectionStatus.DISCONNECTED, ConnectionStatus.DISCONNECTED, ConnectionStatus.INIT_FAILED
        )
        val repo = RobotConnectionRepositoryImpl(controller, pollIntervalMillis = 10)

        assertEquals(listOf(RobotLinkState.DISCONNECTED, RobotLinkState.INIT_FAILED), repo.linkState.take(2).toList())
    }

    @Test
    fun `no communication evidence exists until something succeeds`() = runTest {
        val controller = mock(C40RobotController::class.java)
        `when`(controller.status).thenReturn(ConnectionStatus.CONNECTED)
        val tracker = CommunicationTracker { 777L }
        val repo = RobotConnectionRepositoryImpl(controller, tracker, pollIntervalMillis = 10)

        assertEquals(null, repo.lastCommunicationMillis.first())
        tracker.markSuccess()
        assertEquals(777L, repo.lastCommunicationMillis.first())
    }
}
