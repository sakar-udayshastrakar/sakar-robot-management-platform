package com.sakarrobotics.installation.core.hardware

/**
 * FUTURE hardware-integration seam - deliberately NOT implemented in Phase 1.
 *
 * A real backend (live mapping, sensor access, localization) must not be added until it has been
 * authorised in writing and its interfaces verified (see the Phase 0 audit, sections 12 and 14).
 * Until then this port only exists so the boundary is explicit and reviewable:
 *
 *  - [enabled] is always false;
 *  - there are no transport, ROS, ADB, SSH or socket types anywhere in this project;
 *  - nothing in the app wires an implementation of this interface into a repository.
 *
 * Prerequisites for a future implementation are documented in docs/FUTURE_HARDWARE_INTEGRATION.md.
 */
interface RobotHardwarePort {
    /** True only for an implementation that has been authorised and is deliberately switched on. */
    val enabled: Boolean

    /** Human-readable reason shown if anything asks why hardware is unavailable. */
    val disabledReason: String
}

/** The only [RobotHardwarePort] that exists: permanently disabled. */
object DisabledRobotHardwarePort : RobotHardwarePort {
    override val enabled: Boolean = false
    override val disabledReason: String =
        "Hardware integration is not implemented or authorised. This release is simulation only."
}
