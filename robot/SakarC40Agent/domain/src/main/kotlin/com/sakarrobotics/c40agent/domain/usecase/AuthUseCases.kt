package com.sakarrobotics.c40agent.domain.usecase

import com.sakarrobotics.c40agent.domain.model.UserRole
import com.sakarrobotics.c40agent.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow

class AuthUseCases(private val repository: AuthRepository) {
    fun observeRole(): Flow<UserRole> = repository.role

    suspend fun isSuperUserConfigured(): Boolean = repository.isSuperUserPinConfigured()

    suspend fun setPin(pin: String): Result<Unit> {
        if (pin.length < 4) return Result.failure(IllegalArgumentException("PIN must be at least 4 digits"))
        repository.setSuperUserPin(pin)
        return Result.success(Unit)
    }

    suspend fun tryEnterSuperUser(pin: String): Boolean {
        val ok = repository.verifySuperUserPin(pin)
        if (ok) repository.enterSuperUserMode()
        return ok
    }

    fun exitSuperUser() = repository.exitSuperUserMode()
}
