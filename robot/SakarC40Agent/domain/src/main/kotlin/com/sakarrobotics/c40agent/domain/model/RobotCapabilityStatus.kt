package com.sakarrobotics.c40agent.domain.model

/**
 * Status vocabulary for the future-ROS-integration seam ([com.sakarrobotics.c40agent.domain.gateway.SakarRobotGateway],
 * [com.sakarrobotics.c40agent.domain.repository.RosRobotAdapter], [com.sakarrobotics.c40agent.domain.repository.RobotMappingRepository],
 * [com.sakarrobotics.c40agent.domain.repository.RobotTelemetryRepository]) - deliberately separate from
 * the existing [Capability] enum used throughout the rest of this app's UI.
 *
 * This is NOT a replacement for [Capability] and does not change how any existing screen renders a
 * [Capability] badge. It exists because the ROS/mapping/telemetry seam needs two states [Capability]
 * has no equivalent for:
 *  - [NOT_AVAILABLE]: the capability is confirmed absent (e.g. the licensed Peanut SDK has no live
 *    SLAM API at all - this is a permanent, evidence-backed "no", not a temporary gap).
 *  - [NOT_VERIFIED]: the capability may exist once the real ROS/Robot Computer is identified and
 *    reachable, but nothing here has confirmed it yet - this is an open question, not a "no".
 *
 * Rough correspondence to [Capability], for readers moving between the two vocabularies:
 * [REAL] <-> `Capability.REAL`, [SIMULATED] <-> `Capability.SIMULATED`, [NOT_AVAILABLE] <->
 * `Capability.UNAVAILABLE`. [NOT_VERIFIED] has no [Capability] equivalent - the rest of this app's
 * UI has never needed to distinguish "confirmed absent" from "not yet investigated", but the ROS
 * integration seam must, per this project's own evidence-status discipline
 * (see docs/engineering/01_MASTER_ENGINEERING_KNOWLEDGE_BASE.md's status vocabulary).
 */
enum class RobotCapabilityStatus {
    /** Backed by a confirmed, live data path - never returned by a stub/placeholder implementation. */
    REAL,

    /** No real hardware/ROS path exists yet; this is an explicitly-labeled local simulation. */
    SIMULATED,

    /** Confirmed absent - e.g. no SDK/ROS API for this exists at all, per existing evidence. */
    NOT_AVAILABLE,

    /** Not yet confirmed either way - requires physical robot / ROS endpoint verification. */
    NOT_VERIFIED
}
