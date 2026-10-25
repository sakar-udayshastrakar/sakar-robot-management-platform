package com.sakarrobotics.c40agent.domain.voice

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VoiceServiceTest {

    private val service = MockVoiceService()

    @Test
    fun `announce resolves the event text when enabled`() {
        val outcome = service.announce(VoiceEvent.CLEANING_STARTED, VoiceSettings(volumePercent = 80, muted = false))
        assertTrue(outcome is VoiceOutcome.Resolved)
        outcome as VoiceOutcome.Resolved
        assertEquals(VoiceEvent.CLEANING_STARTED.defaultText, outcome.text)
        assertEquals(80, outcome.volumePercent)
        assertEquals(VoiceEvent.CLEANING_STARTED.defaultText, service.lastResolved)
    }

    @Test
    fun `announce is suppressed when muted`() {
        val outcome = service.announce(VoiceEvent.LOW_BATTERY, VoiceSettings(volumePercent = 100, muted = true))
        assertEquals(VoiceOutcome.Suppressed, outcome)
        assertNull(service.lastResolved)
    }

    @Test
    fun `announce is suppressed at zero volume`() {
        val outcome = service.announce(VoiceEvent.FAULT, VoiceSettings(volumePercent = 0, muted = false))
        assertEquals(VoiceOutcome.Suppressed, outcome)
        assertNull(service.lastResolved)
    }

    @Test
    fun `every event has non-blank Sakar-authored text`() {
        assertTrue(VoiceEvent.values().all { it.defaultText.isNotBlank() })
    }
}
