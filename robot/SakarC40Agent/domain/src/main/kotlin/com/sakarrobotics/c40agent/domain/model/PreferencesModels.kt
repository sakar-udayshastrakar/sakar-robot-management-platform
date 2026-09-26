package com.sakarrobotics.c40agent.domain.model

enum class ScreenFace { BLUE_EYES, GREEN_EYES, SLEEPY, OFF }

enum class BusinessProfile { SR_CLEANING, SR_BUTLER, DEMO }

/** Sakar-local device preferences - real local state (DataStore), no Keenon "resource package" concept involved. */
data class LocalPreferences(
    val language: String = "English",
    val musicVolume: Int = 50,
    val voiceVolume: Int = 80,
    val screenLockDurationSeconds: Int = 15,
    val screenLockPinConfigured: Boolean = false,
    val screenFace: ScreenFace = ScreenFace.BLUE_EYES,
    val promotionalContentEnabled: Boolean = false,
    val musicDndEnabled: Boolean = false,
    val taskDndEnabled: Boolean = false,
    val resumeFromBreakpoint: Boolean = false,
    val sweepingBrushAutoHeight: Boolean = true,
    val businessProfile: BusinessProfile = BusinessProfile.SR_CLEANING
)
