package com.sakarrobotics.c40agent.domain.model

/**
 * Every value and action surfaced by the Sakar operator UI must declare
 * how real it is. The UI renders this directly (see CapabilityBadge in
 * :operator-ui) next to the data/control it describes - nothing in this
 * app is allowed to show a robot capability as working when it is not.
 */
enum class Capability {
    /** Backed by a confirmed Peanut SDK call against [com.sakarrobotics.c40agent.robot.C40RobotController]. */
    REAL,

    /**
     * No corresponding Peanut SDK/hardware API exists (verified against
     * COMPATIBILITY_REPORT.md); this runs as a local, clearly-labeled
     * software simulation so the UI flow can be exercised end to end.
     */
    SIMULATED,

    /** Real local data (e.g. Room-backed schedules/consumables) with no robot hardware involved. */
    LOCAL,

    /** Requires [com.sakarrobotics.c40agent.robot.OperatingMode.HARDWARE_TEST], which nothing in this app enables automatically. */
    GATED,

    /** No SDK or hardware API exists for this yet. The UI still exposes the screen/interface, disabled, for future wiring. */
    UNAVAILABLE
}

/** A value paired with how real it is, so the UI never has to guess. */
data class Rated<T>(val value: T, val capability: Capability)
