package com.sakarrobotics.c40agent.domain.model

/**
 * Connection lifecycle state for [com.sakarrobotics.c40agent.domain.repository.RosRobotAdapter] -
 * mirrors [RobotLinkState]'s naming for consistency, but is a distinct enum because this describes
 * the (future, currently stubbed) link to the separate ROS/Robot Computer
 * (docs/engineering/03_HARDWARE_ARCHITECTURE.md §2), not the existing Peanut-SDK link
 * [RobotLinkState] already describes.
 */
enum class RosConnectionState { DISCONNECTED, CONNECTING, CONNECTED, FAILED }

/**
 * Where to reach the ROS/Robot Computer, sourced externally (future settings/remote-config) - never
 * defaulted to a hardcoded value. `192.168.64.20`/`9090`/`9091` are historical reverse-engineering
 * references only (docs/engineering/04_SLAM_INVESTIGATION_STATUS.md §4) and are NOT confirmed as the
 * current physical robot's endpoint - this type exists specifically so no code path can fall back to
 * them silently. All fields are nullable and default to null/unset; a null [host] means "no endpoint
 * configured yet", which [com.sakarrobotics.c40agent.domain.repository.RosRobotAdapter] implementations
 * must treat as [RobotCapabilityStatus.NOT_VERIFIED], never as a reason to substitute a historical value.
 */
data class RosEndpointConfig(
    val host: String? = null,
    val primaryPort: Int? = null,
    val secondaryPort: Int? = null
)

/**
 * One entry from [com.sakarrobotics.c40agent.domain.repository.RosRobotAdapter.discoverCapabilities] -
 * a name plus whether it was actually confirmed present, never a guessed/assumed list of ROS
 * topics or services (see this project's own rule against inventing ROS topics/services/ports).
 */
data class RosCapabilityDescriptor(
    val name: String,
    val status: RobotCapabilityStatus
)
