package com.sakarrobotics.c40agent.domain.model

/** Which backend RobotNavigationRepository.jog() actually drives. Selected once, at container construction. */
enum class ManualDriveBackendMode { REAL, SIMULATED }

enum class SimulatedMovementState { IDLE, MOVING }

/**
 * A deterministic, software-only virtual robot used to test Manual Drive's control flow with no
 * real hardware involved. None of these values are derived from real robot calibration, physics,
 * or measurements - they exist only so operator-ui and engineering tooling have something honest
 * to display while SIMULATED is the active backend. Units are meters/degrees/seconds, arbitrary
 * but internally consistent.
 */
data class SimulatedRobotState(
    val x: Double = 0.0,
    val y: Double = 0.0,
    val headingDegrees: Double = 0.0,
    val linearVelocity: Double = 0.0,
    val angularVelocity: Double = 0.0,
    val encoderLeft: Long = 0,
    val encoderRight: Long = 0,
    val movementState: SimulatedMovementState = SimulatedMovementState.IDLE,
    val lastCommand: ManualDriveDirection? = null,
    val elapsedMovementTimeMs: Long = 0
)
