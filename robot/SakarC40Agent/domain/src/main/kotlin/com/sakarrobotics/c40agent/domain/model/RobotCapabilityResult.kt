package com.sakarrobotics.c40agent.domain.model

/**
 * Result wrapper for the ROS/mapping/telemetry seam that makes "fake success" structurally
 * impossible to return: a caller can only obtain a [T] value via [Available], and [Available]
 * can only be constructed with [RobotCapabilityStatus.REAL] or [RobotCapabilityStatus.SIMULATED].
 * There is no code path that lets an implementation hand back a placeholder [T] tagged as REAL, or
 * a value at all when the capability is [RobotCapabilityStatus.NOT_AVAILABLE]/[RobotCapabilityStatus.NOT_VERIFIED].
 *
 * This is deliberately a different shape from the existing [Rated]`<T>` (which always carries a
 * value alongside a [Capability]) - [Rated] fits capabilities that always have *something* to show
 * (even if simulated), whereas mapping/ROS capabilities that are simply not available yet must not
 * be forced to manufacture a placeholder [T] just to satisfy [Rated]'s shape.
 */
sealed class RobotCapabilityResult<out T> {
    data class Available<T>(val value: T, val status: RobotCapabilityStatus) : RobotCapabilityResult<T>() {
        init {
            require(status == RobotCapabilityStatus.REAL || status == RobotCapabilityStatus.SIMULATED) {
                "Available<T> must carry REAL or SIMULATED, got $status - use Unavailable for NOT_AVAILABLE/NOT_VERIFIED"
            }
        }
    }

    data class Unavailable(val status: RobotCapabilityStatus, val reason: String) : RobotCapabilityResult<Nothing>() {
        init {
            require(status == RobotCapabilityStatus.NOT_AVAILABLE || status == RobotCapabilityStatus.NOT_VERIFIED) {
                "Unavailable must carry NOT_AVAILABLE or NOT_VERIFIED, got $status - use Available for REAL/SIMULATED"
            }
        }
    }
}
