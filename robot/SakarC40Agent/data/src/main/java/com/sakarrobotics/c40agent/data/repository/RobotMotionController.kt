package com.sakarrobotics.c40agent.data.repository

import android.util.Log
import com.sakarrobotics.c40agent.domain.model.ManualDriveDirection
import com.sakarrobotics.c40agent.domain.model.SimulatedMovementState
import com.sakarrobotics.c40agent.domain.model.SimulatedRobotState
import com.sakarrobotics.c40agent.robot.C40RobotController
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The single seam RobotNavigationRepositoryImpl.jog() calls through - selected once at
 * DefaultAppContainer construction time by ManualDriveBackendMode, never per-call. This is what
 * lets the exact same UI/ViewModel/use-case chain drive either a real robot or a virtual one
 * with zero UI changes.
 */
interface RobotMotionController {
    suspend fun forward(source: String): Result<Unit>
    suspend fun backward(source: String): Result<Unit>
    suspend fun turnLeft(source: String): Result<Unit>
    suspend fun turnRight(source: String): Result<Unit>
    suspend fun stop(source: String): Result<Unit>
}

/**
 * REAL backend - thin pass-through to C40RobotController's already-gated, already-logged motor
 * methods (OperatingMode guard + SDK-readiness check live there, not here - see
 * C40RobotController.motorForward et al.).
 */
class RealMotorController(private val controller: C40RobotController) : RobotMotionController {
    override suspend fun forward(source: String): Result<Unit> = awaitSdkCall { controller.motorForward(source, it) }.map { }
    override suspend fun backward(source: String): Result<Unit> = awaitSdkCall { controller.motorBackward(source, it) }.map { }
    override suspend fun turnLeft(source: String): Result<Unit> = awaitSdkCall { controller.motorTurnLeft(source, it) }.map { }
    override suspend fun turnRight(source: String): Result<Unit> = awaitSdkCall { controller.motorTurnRight(source, it) }.map { }
    override suspend fun stop(source: String): Result<Unit> = awaitSdkCall { controller.motorStop(source, it) }.map { }
}

/**
 * SIMULATED backend - a deterministic virtual robot. Never imports or calls anything from
 * com.keenon.sdk.*, never touches C40RobotController/PeanutSdkBridge: this class has no reference
 * to either, so there is no code path here that could reach MotorComponent even by accident.
 *
 * Each command call is a single, immediate state update (matching the real backend's one-command-
 * per-press contract - see ManualDriveViewModel), after which a fixed-timestep coroutine advances
 * position/heading/encoders while movementState == MOVING, until the next command (including
 * STOP) replaces it. Values are arbitrary, self-consistent units - not calibrated to any real
 * C40 robot.
 */
class SimulatedMotorController(private val scope: CoroutineScope) : RobotMotionController {

    private companion object {
        const val TAG = "ManualDriveSim"
        const val TICK_MS = 100L
        const val LINEAR_SPEED = 0.3 // simulated m/s
        const val ANGULAR_SPEED = 45.0 // simulated deg/s
        const val ENCODER_TICKS_PER_METER = 1000.0
    }

    private val _state = MutableStateFlow(SimulatedRobotState())
    val state: StateFlow<SimulatedRobotState> = _state.asStateFlow()

    private var tickJob: Job? = null

    override suspend fun forward(source: String): Result<Unit> =
        command(source, ManualDriveDirection.FORWARD, linear = LINEAR_SPEED, angular = 0.0)

    override suspend fun backward(source: String): Result<Unit> =
        command(source, ManualDriveDirection.REVERSE, linear = -LINEAR_SPEED, angular = 0.0)

    override suspend fun turnLeft(source: String): Result<Unit> =
        command(source, ManualDriveDirection.LEFT, linear = 0.0, angular = ANGULAR_SPEED)

    override suspend fun turnRight(source: String): Result<Unit> =
        command(source, ManualDriveDirection.RIGHT, linear = 0.0, angular = -ANGULAR_SPEED)

    override suspend fun stop(source: String): Result<Unit> =
        command(source, ManualDriveDirection.STOP, linear = 0.0, angular = 0.0)

    private fun command(source: String, direction: ManualDriveDirection, linear: Double, angular: Double): Result<Unit> {
        Log.i(TAG, "SIMULATED_MOTOR_COMMAND direction=$direction source=$source linear=$linear angular=$angular")
        tickJob?.cancel()
        _state.update {
            it.copy(
                linearVelocity = linear,
                angularVelocity = angular,
                movementState = if (direction == ManualDriveDirection.STOP) SimulatedMovementState.IDLE else SimulatedMovementState.MOVING,
                lastCommand = direction,
                elapsedMovementTimeMs = 0
            )
        }
        if (direction != ManualDriveDirection.STOP) {
            tickJob = scope.launch {
                var elapsed = 0L
                while (isActive) {
                    delay(TICK_MS)
                    elapsed += TICK_MS
                    _state.update { advance(it, TICK_MS, elapsed) }
                }
            }
        }
        return Result.success(Unit)
    }

    private fun advance(s: SimulatedRobotState, dtMs: Long, elapsedMs: Long): SimulatedRobotState {
        val dtSec = dtMs / 1000.0
        val headingRad = Math.toRadians(s.headingDegrees)
        val newHeading = (s.headingDegrees + s.angularVelocity * dtSec).mod(360.0)
        val newX = s.x + s.linearVelocity * cos(headingRad) * dtSec
        val newY = s.y + s.linearVelocity * sin(headingRad) * dtSec
        val ticks = (abs(s.linearVelocity) * dtSec * ENCODER_TICKS_PER_METER).toLong()
        return s.copy(
            x = newX,
            y = newY,
            headingDegrees = newHeading,
            encoderLeft = s.encoderLeft + ticks,
            encoderRight = s.encoderRight + ticks,
            elapsedMovementTimeMs = elapsedMs
        )
    }
}
