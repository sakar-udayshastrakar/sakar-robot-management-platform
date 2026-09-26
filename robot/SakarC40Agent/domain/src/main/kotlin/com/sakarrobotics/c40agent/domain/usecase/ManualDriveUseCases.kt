package com.sakarrobotics.c40agent.domain.usecase

import com.sakarrobotics.c40agent.domain.model.ManualDriveDirection
import com.sakarrobotics.c40agent.domain.repository.RobotNavigationRepository
import kotlin.math.max
import kotlin.math.min

/**
 * Converts a discrete jog direction + speed level into the linear/angular
 * velocity pair RobotNavigationRepository.jog expects, and always issues
 * an explicit STOP on release rather than relying on a timeout - this is
 * the only manual-motion entry point in the app, so it is the one place
 * that speed clamping and the release-to-stop rule are enforced.
 */
class ManualDriveUseCases(private val navigationRepository: RobotNavigationRepository) {

    suspend fun jog(direction: ManualDriveDirection, speedLevel: Int): Result<Unit> {
        val speed = min(max(speedLevel, 1), 3) / 3f
        val (linear, angular) = when (direction) {
            ManualDriveDirection.FORWARD -> speed to 0f
            ManualDriveDirection.REVERSE -> -speed to 0f
            ManualDriveDirection.LEFT -> 0f to speed
            ManualDriveDirection.RIGHT -> 0f to -speed
            ManualDriveDirection.STOP -> 0f to 0f
        }
        return navigationRepository.jog(linear, angular)
    }

    suspend fun emergencyStop(): Result<Unit> = navigationRepository.jog(0f, 0f)
}
