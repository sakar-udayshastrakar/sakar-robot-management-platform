package com.sakarrobotics.c40agent.domain.voice

/**
 * Robot voice-announcement abstraction.
 *
 * DATA-HONESTY: no verified on-device voice/TTS backend is wired in this app. The robot's own
 * voice engine (vendor iflytek offline / Azure Speech over Android AIDL) is out of scope and
 * unverified here, so the only implementation today is [MockVoiceService], which plays NO audio
 * and contacts NO robot - it only resolves what WOULD be announced, honouring the operator's
 * volume/mute preference. A real implementation can replace the mock behind this interface
 * without changing callers. See research/c40-reverse-engineering/ui/VOICE_MODULE_ANALYSIS.md.
 */
interface VoiceService {
    fun announce(event: VoiceEvent, settings: VoiceSettings): VoiceOutcome
}

/**
 * Sakar-authored announcement events. The original robot ships numbered audio prompts whose exact
 * spoken text is NOT present in the static reverse-engineering evidence (see
 * research/c40-reverse-engineering/ui/VOICE_ASSET_INVENTORY.csv - announcement_text = UNKNOWN).
 * The strings below are therefore Sakar's OWN wording, grouped by the events the cleaning flow is
 * known to trigger - not recovered vendor audio.
 */
enum class VoiceEvent(val defaultText: String) {
    CLEANING_STARTED("Starting cleaning task."),
    CLEANING_PAUSED("Cleaning paused."),
    CLEANING_RESUMED("Resuming cleaning."),
    CLEANING_COMPLETED("Cleaning complete."),
    RETURNING_TO_DOCK("Returning to the charging dock."),
    CHARGING("Charging."),
    LOW_BATTERY("Battery low. Please let me charge."),
    OBSTACLE_DETECTED("Obstacle detected. Please clear my path."),
    FAULT("A fault has occurred. Please check the robot."),
    MAINTENANCE_DUE("A consumable part needs attention soon."),
    SYSTEM_READY("System ready.")
}

/** Operator voice preferences - mirrors the persisted voiceVolume + DND user preferences. */
data class VoiceSettings(
    val volumePercent: Int,
    val muted: Boolean
)

/** Result of an [VoiceService.announce] call - never a claim that the robot physically spoke. */
sealed interface VoiceOutcome {
    /** The service resolved the text it WOULD speak (the mock produces no actual audio). */
    data class Resolved(val text: String, val volumePercent: Int) : VoiceOutcome

    /** Suppressed because the operator muted announcements or set volume to zero. */
    data object Suppressed : VoiceOutcome
}
