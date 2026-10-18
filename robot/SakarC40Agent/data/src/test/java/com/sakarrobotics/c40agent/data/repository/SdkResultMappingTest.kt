package com.sakarrobotics.c40agent.data.repository

import com.sakarrobotics.c40agent.domain.model.Capability
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SdkResultMappingTest {

    private val failure: Result<String> = Result.failure(SdkCallException(-1, "not connected"))

    @Test
    fun `a failed sensor read is never labelled real`() {
        val rated = failure.toRatedReading()
        assertEquals(Capability.UNAVAILABLE, rated.capability)
        assertTrue(rated.value.startsWith("error:"))
    }

    @Test
    fun `a successful sensor read with data is real and verbatim`() {
        val rated = Result.success("{\"range\":[1,2,3]}").toRatedReading()
        assertEquals(Capability.REAL, rated.capability)
        assertEquals("{\"range\":[1,2,3]}", rated.value)
    }

    @Test
    fun `an empty successful read is unknown rather than real`() {
        assertEquals(Capability.UNKNOWN, Result.success("").toRatedReading().capability)
        assertEquals(Capability.UNKNOWN, Result.success("   ").toRatedReading().capability)
    }

    @Test
    fun `battery payload is never parsed and stays unknown even when the sdk answers`() {
        val state = Result.success("{\"capacity\":87,\"charging\":true}").toBatteryState()
        assertNull(state.percentage)
        assertNull(state.isCharging)
        assertNull(state.temperatureCelsius)
        assertEquals(Capability.UNKNOWN, state.capability)
        assertEquals("{\"capacity\":87,\"charging\":true}", state.raw)
    }

    @Test
    fun `battery query failure is unavailable with the reason`() {
        val state = failure.toBatteryState()
        assertEquals(Capability.UNAVAILABLE, state.capability)
        assertNull(state.percentage)
        assertNull(state.isCharging)
        assertTrue(state.raw!!.contains("not connected"))
    }

    @Test
    fun `initial battery state is unknown with no charging claim`() {
        val state = initialBatteryState()
        assertEquals(Capability.UNKNOWN, state.capability)
        assertNull(state.isCharging)
    }

    @Test
    fun `map info success carries raw text only and infers nothing`() {
        val result = Result.success("<map-info/>").toMapListResult()
        assertNull(result.error)
        val map = result.maps.single()
        assertEquals("<map-info/>", map.rawInfo)
        assertNull(map.sizeBytes)
        assertNull(map.inUse)
        assertEquals(Capability.UNKNOWN, map.capability)
    }

    @Test
    fun `map info failure and empty response surface an error and no maps`() {
        val failed = failure.toMapListResult()
        assertTrue(failed.maps.isEmpty())
        assertTrue(failed.error!!.contains("not connected"))

        val empty = Result.success("").toMapListResult()
        assertTrue(empty.maps.isEmpty())
        assertFalse(empty.error.isNullOrBlank())
    }

    @Test
    fun `actuator test result mirrors success and failure`() {
        val ok = Result.success("fine").toActuatorTestResult("imu_read", 5L)
        assertTrue(ok.success)
        assertEquals("fine", ok.message)

        val bad = Result.failure<String>(UnsupportedOperationException("nope")).toActuatorTestResult("x", 6L)
        assertFalse(bad.success)
        assertEquals("nope", bad.message)
    }
}
