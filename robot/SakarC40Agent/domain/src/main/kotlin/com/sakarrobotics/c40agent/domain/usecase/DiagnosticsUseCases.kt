package com.sakarrobotics.c40agent.domain.usecase

import com.sakarrobotics.c40agent.domain.model.ActuatorGroup
import com.sakarrobotics.c40agent.domain.model.ActuatorTest
import com.sakarrobotics.c40agent.domain.model.ActuatorTestResult
import com.sakarrobotics.c40agent.domain.model.DiagnosticsSnapshot
import com.sakarrobotics.c40agent.domain.model.SensorReadings
import com.sakarrobotics.c40agent.domain.repository.RobotDiagnosticsRepository
import com.sakarrobotics.c40agent.domain.repository.RobotSensorsRepository

class DiagnosticsUseCases(
    private val diagnosticsRepository: RobotDiagnosticsRepository,
    private val sensorsRepository: RobotSensorsRepository
) {
    suspend fun snapshot(): DiagnosticsSnapshot = diagnosticsRepository.snapshot()

    suspend fun sensors(): SensorReadings = sensorsRepository.readSensors()

    fun groups(): List<ActuatorGroup> = diagnosticsRepository.actuatorGroups()

    fun tests(groupId: String): List<ActuatorTest> = diagnosticsRepository.actuatorTests(groupId)

    /** Caller (UI) must have already shown a confirmation dialog when [ActuatorTest.requiresConfirmation] is true. */
    suspend fun runTest(testId: String): ActuatorTestResult = diagnosticsRepository.runActuatorTest(testId)
}
