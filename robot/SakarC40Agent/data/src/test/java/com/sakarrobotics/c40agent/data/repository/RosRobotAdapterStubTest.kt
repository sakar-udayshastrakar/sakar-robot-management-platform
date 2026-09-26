package com.sakarrobotics.c40agent.data.repository

import com.sakarrobotics.c40agent.domain.model.RobotCapabilityResult
import com.sakarrobotics.c40agent.domain.model.RobotCapabilityStatus
import com.sakarrobotics.c40agent.domain.model.RosConnectionState
import com.sakarrobotics.c40agent.domain.model.RosEndpointConfig
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

/**
 * Confirms the stub never attempts a real connection and never fabricates a capability list -
 * see [RosRobotAdapterStub]'s own KDoc for why this is intentional at this architecture phase.
 */
class RosRobotAdapterStubTest {

    @Test
    fun initialConnectionState_isDisconnected() {
        val adapter = RosRobotAdapterStub()
        assertEquals(RosConnectionState.DISCONNECTED, adapter.connectionState.value)
    }

    @Test
    fun connect_withNoHostConfigured_returnsUnavailableNotVerified() = runTest {
        val adapter = RosRobotAdapterStub()
        val result = adapter.connect(RosEndpointConfig())
        assertInstanceOf(RobotCapabilityResult.Unavailable::class.java, result)
        assertEquals(RobotCapabilityStatus.NOT_VERIFIED, (result as RobotCapabilityResult.Unavailable).status)
    }

    @Test
    fun connect_withHostConfigured_stillReturnsUnavailable_noRealProtocolImplementedYet() = runTest {
        val adapter = RosRobotAdapterStub()
        // Deliberately NOT the historical 192.168.64.20 - any host should behave the same way,
        // since this stub never opens a real connection regardless of what is configured.
        val result = adapter.connect(RosEndpointConfig(host = "10.0.0.5", primaryPort = 9090))
        assertInstanceOf(RobotCapabilityResult.Unavailable::class.java, result)
        assertEquals(RosConnectionState.DISCONNECTED, adapter.connectionState.value)
    }

    @Test
    fun discoverCapabilities_returnsUnavailable_neverAGuessedList() = runTest {
        val adapter = RosRobotAdapterStub()
        val result = adapter.discoverCapabilities()
        assertInstanceOf(RobotCapabilityResult.Unavailable::class.java, result)
    }

    @Test
    fun disconnect_leavesStateDisconnected() = runTest {
        val adapter = RosRobotAdapterStub()
        adapter.disconnect()
        assertEquals(RosConnectionState.DISCONNECTED, adapter.connectionState.value)
    }
}
