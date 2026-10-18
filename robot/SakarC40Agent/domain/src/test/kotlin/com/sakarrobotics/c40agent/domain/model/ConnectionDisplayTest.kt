package com.sakarrobotics.c40agent.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ConnectionDisplayTest {

    @Test
    fun `connected link without any successful exchange is unknown not online`() {
        assertEquals(ConnectionDisplayState.UNKNOWN, deriveConnectionDisplay(RobotLinkState.CONNECTED, null))
    }

    @Test
    fun `connected link with a successful exchange is online`() {
        assertEquals(ConnectionDisplayState.ONLINE, deriveConnectionDisplay(RobotLinkState.CONNECTED, 1_000L))
    }

    @Test
    fun `disconnected and failed init are offline even when a past exchange exists`() {
        assertEquals(ConnectionDisplayState.OFFLINE, deriveConnectionDisplay(RobotLinkState.DISCONNECTED, 1_000L))
        assertEquals(ConnectionDisplayState.OFFLINE, deriveConnectionDisplay(RobotLinkState.INIT_FAILED, 1_000L))
        assertEquals(ConnectionDisplayState.OFFLINE, deriveConnectionDisplay(RobotLinkState.INIT_FAILED, null))
    }

    @Test
    fun `connecting is connecting`() {
        assertEquals(ConnectionDisplayState.CONNECTING, deriveConnectionDisplay(RobotLinkState.CONNECTING, null))
    }

    @Test
    fun `every link state has a readable name`() {
        RobotLinkState.values().forEach { state ->
            val name = state.displayName()
            assertEquals(false, name.contains('_'), "$state -> $name")
            assertEquals(true, name.first().isUpperCase())
        }
    }

    @Test
    fun `formatAge buckets`() {
        val now = 10_000_000L
        assertEquals("just now", formatAge(now - 2_000, now))
        assertEquals("30s ago", formatAge(now - 30_000, now))
        assertEquals("5m ago", formatAge(now - 5 * 60_000, now))
        assertEquals("3h ago", formatAge(now - 3 * 3_600_000L, now))
        assertEquals("2d ago", formatAge(now - 2 * 86_400_000L, now))
    }

    @Test
    fun `formatAge treats a future timestamp as just now`() {
        assertEquals("just now", formatAge(5_000L, 1_000L))
    }

    @Test
    fun `battery readout never invents a percentage`() {
        val unknown = BatteryState(null, null, null, "{\"x\":1}", Capability.UNKNOWN)
        val unavailable = BatteryState(null, null, null, "error", Capability.UNAVAILABLE)
        val realWithoutValue = BatteryState(null, null, null, null, Capability.REAL)
        val verified = BatteryState(73, false, null, null, Capability.REAL)
        val unverifiedWithValue = BatteryState(73, false, null, null, Capability.UNKNOWN)

        assertEquals("UNKNOWN", batteryReadout(unknown))
        assertEquals("UNAVAILABLE", batteryReadout(unavailable))
        assertEquals("UNKNOWN", batteryReadout(realWithoutValue))
        assertEquals("73%", batteryReadout(verified))
        assertEquals("UNKNOWN", batteryReadout(unverifiedWithValue))
    }

    @Test
    fun `robot capability status maps onto the ui capability vocabulary`() {
        assertEquals(Capability.REAL, RobotCapabilityStatus.REAL.toCapability())
        assertEquals(Capability.SIMULATED, RobotCapabilityStatus.SIMULATED.toCapability())
        assertEquals(Capability.UNAVAILABLE, RobotCapabilityStatus.NOT_AVAILABLE.toCapability())
        assertEquals(Capability.UNKNOWN, RobotCapabilityStatus.NOT_VERIFIED.toCapability())
    }
}
