package com.sakarrobotics.c40agent.domain.model

/** The four connection states shown to the operator. UNKNOWN means "no evidence either way", not "probably fine". */
enum class ConnectionDisplayState { ONLINE, OFFLINE, CONNECTING, UNKNOWN }

/**
 * Derives what the UI may claim about the robot link. The SDK link state alone is not enough to say
 * ONLINE: [RobotLinkState.CONNECTED] only records that initialization succeeded once. ONLINE
 * additionally requires evidence of at least one successful SDK exchange ([lastCommunicationMillis]
 * non-null); a CONNECTED link with no such evidence is UNKNOWN.
 *
 * No time-based downgrade is applied here on purpose: nothing in this app polls the robot in the
 * background, so silence is not proof of a dropped link. Callers show the age of the last
 * communication instead (see [formatAge]).
 */
fun deriveConnectionDisplay(link: RobotLinkState, lastCommunicationMillis: Long?): ConnectionDisplayState = when (link) {
    RobotLinkState.CONNECTING -> ConnectionDisplayState.CONNECTING
    RobotLinkState.DISCONNECTED, RobotLinkState.INIT_FAILED -> ConnectionDisplayState.OFFLINE
    RobotLinkState.CONNECTED ->
        if (lastCommunicationMillis != null) ConnectionDisplayState.ONLINE else ConnectionDisplayState.UNKNOWN
}

/** Operator-facing name of the raw SDK link state. */
fun RobotLinkState.displayName(): String = when (this) {
    RobotLinkState.DISCONNECTED -> "Disconnected"
    RobotLinkState.CONNECTING -> "Connecting"
    RobotLinkState.CONNECTED -> "Connected (SDK initialised)"
    RobotLinkState.INIT_FAILED -> "SDK initialisation failed"
}

/** Human-readable age of [thenMillis] relative to [nowMillis]; clock skew (future timestamps) reads as "just now". */
fun formatAge(thenMillis: Long, nowMillis: Long): String {
    val seconds = ((nowMillis - thenMillis) / 1000L).coerceAtLeast(0L)
    return when {
        seconds < 5 -> "just now"
        seconds < 60 -> "${seconds}s ago"
        seconds < 3600 -> "${seconds / 60}m ago"
        seconds < 86_400 -> "${seconds / 3600}h ago"
        else -> "${seconds / 86_400}d ago"
    }
}

/**
 * Short battery readout for compact UI (e.g. Home's status area). A percentage is shown only for a
 * REAL, verified reading; everything else reads as UNKNOWN or UNAVAILABLE - never a guessed number.
 */
fun batteryReadout(battery: BatteryState): String {
    val percentage = battery.percentage
    return when {
        battery.capability == Capability.REAL && percentage != null -> "$percentage%"
        battery.capability == Capability.UNAVAILABLE -> "UNAVAILABLE"
        else -> "UNKNOWN"
    }
}
