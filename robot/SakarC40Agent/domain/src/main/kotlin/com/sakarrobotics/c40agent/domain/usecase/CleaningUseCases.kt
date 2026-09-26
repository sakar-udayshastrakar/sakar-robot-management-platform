package com.sakarrobotics.c40agent.domain.usecase

import com.sakarrobotics.c40agent.domain.model.CleaningIntensity
import com.sakarrobotics.c40agent.domain.model.CleaningMode
import com.sakarrobotics.c40agent.domain.model.CleaningSession
import com.sakarrobotics.c40agent.domain.repository.CleaningRepository
import com.sakarrobotics.c40agent.domain.repository.RobotChargingRepository
import kotlinx.coroutines.flow.Flow

class CleaningUseCases(
    private val cleaningRepository: CleaningRepository,
    private val chargingRepository: RobotChargingRepository
) {
    fun observeSession(): Flow<CleaningSession> = cleaningRepository.session

    suspend fun start(zoneIds: List<String>, mode: CleaningMode, intensity: CleaningIntensity, cycles: Int): Result<Unit> {
        require(cycles in 1..99) { "Cleaning cycles must be between 1 and 99" }
        return cleaningRepository.startCleaning(zoneIds, mode, intensity, cycles)
    }

    suspend fun pause(): Result<Unit> = cleaningRepository.pause()

    suspend fun resume(): Result<Unit> = cleaningRepository.resume()

    suspend fun stop(): Result<Unit> = cleaningRepository.stop()

    suspend fun returnToDock(): Result<Unit> = chargingRepository.returnToDock()
}
