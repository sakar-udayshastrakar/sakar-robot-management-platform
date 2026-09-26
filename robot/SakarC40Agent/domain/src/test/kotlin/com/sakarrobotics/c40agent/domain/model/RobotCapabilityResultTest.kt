package com.sakarrobotics.c40agent.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/**
 * Guards the "never return fake success" contract at the type level: [RobotCapabilityResult]
 * must make it impossible to construct an [RobotCapabilityResult.Available] tagged
 * NOT_AVAILABLE/NOT_VERIFIED, or an [RobotCapabilityResult.Unavailable] tagged REAL/SIMULATED.
 */
class RobotCapabilityResultTest {

    @Test
    fun available_withRealStatus_isConstructedSuccessfully() {
        val result = RobotCapabilityResult.Available(value = "x", status = RobotCapabilityStatus.REAL)
        assertEquals(RobotCapabilityStatus.REAL, result.status)
    }

    @Test
    fun available_withSimulatedStatus_isConstructedSuccessfully() {
        val result = RobotCapabilityResult.Available(value = "x", status = RobotCapabilityStatus.SIMULATED)
        assertEquals(RobotCapabilityStatus.SIMULATED, result.status)
    }

    @Test
    fun available_withNotAvailableStatus_throws() {
        assertThrows(IllegalArgumentException::class.java) {
            RobotCapabilityResult.Available(value = "x", status = RobotCapabilityStatus.NOT_AVAILABLE)
        }
    }

    @Test
    fun available_withNotVerifiedStatus_throws() {
        assertThrows(IllegalArgumentException::class.java) {
            RobotCapabilityResult.Available(value = "x", status = RobotCapabilityStatus.NOT_VERIFIED)
        }
    }

    @Test
    fun unavailable_withNotAvailableStatus_isConstructedSuccessfully() {
        val result = RobotCapabilityResult.Unavailable(status = RobotCapabilityStatus.NOT_AVAILABLE, reason = "no SDK API")
        assertEquals(RobotCapabilityStatus.NOT_AVAILABLE, result.status)
    }

    @Test
    fun unavailable_withRealStatus_throws() {
        assertThrows(IllegalArgumentException::class.java) {
            RobotCapabilityResult.Unavailable(status = RobotCapabilityStatus.REAL, reason = "wrong bucket")
        }
    }

    @Test
    fun unavailable_withSimulatedStatus_throws() {
        assertThrows(IllegalArgumentException::class.java) {
            RobotCapabilityResult.Unavailable(status = RobotCapabilityStatus.SIMULATED, reason = "wrong bucket")
        }
    }
}
