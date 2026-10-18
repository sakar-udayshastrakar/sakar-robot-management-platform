package com.sakarrobotics.c40agent.data.repository

import com.sakarrobotics.c40agent.robot.C40RobotController
import com.sakarrobotics.c40agent.sdk.SdkCallback
import com.sakarrobotics.c40agent.telemetry.ConnectionStatus
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

/** The controller is mocked: no Peanut SDK is initialised and nothing talks to a robot. */
class RobotMapRepositoryImplTest {

    @Test
    fun `map info is not requested while the sdk is not connected`() = runTest {
        val controller = mock(C40RobotController::class.java)
        `when`(controller.status).thenReturn(ConnectionStatus.INIT_FAILED)
        val repo = RobotMapRepositoryImpl(controller)

        val result = repo.listMapsDetailed()

        assertTrue(result.maps.isEmpty())
        assertTrue(result.error!!.contains("Not connected"))
        verify(controller, never()).getMapInfo(any(SdkCallback::class.java))
    }

    @Test
    fun `a connected link reads map info and keeps it raw`() = runTest {
        val controller = mock(C40RobotController::class.java)
        `when`(controller.status).thenReturn(ConnectionStatus.CONNECTED)
        org.mockito.Mockito.doAnswer { invocation ->
            invocation.getArgument<SdkCallback>(0).onSuccess("raw-map-info")
            null
        }.`when`(controller).getMapInfo(any(SdkCallback::class.java))
        val tracker = CommunicationTracker { 5L }
        val repo = RobotMapRepositoryImpl(controller, tracker)

        val result = repo.listMapsDetailed()

        assertEquals("raw-map-info", result.maps.single().rawInfo)
        assertEquals(5L, tracker.current)
    }
}
