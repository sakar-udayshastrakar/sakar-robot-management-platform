package com.sakarrobotics.c40agent.domain.voice

/**
 * Labeled MOCK [VoiceService]. Produces NO audio and contacts NO robot; it only resolves the text
 * that would be announced, honouring mute/volume. [lastResolved] exposes the most recent resolved
 * text for UI preview / tests. Replace with a real backend behind [VoiceService] when one is
 * verified - do not change this to emit fabricated "spoke on the robot" results.
 */
class MockVoiceService : VoiceService {

    @Volatile
    var lastResolved: String? = null
        private set

    override fun announce(event: VoiceEvent, settings: VoiceSettings): VoiceOutcome {
        if (settings.muted || settings.volumePercent <= 0) return VoiceOutcome.Suppressed
        val text = event.defaultText
        lastResolved = text
        return VoiceOutcome.Resolved(text, settings.volumePercent)
    }
}
