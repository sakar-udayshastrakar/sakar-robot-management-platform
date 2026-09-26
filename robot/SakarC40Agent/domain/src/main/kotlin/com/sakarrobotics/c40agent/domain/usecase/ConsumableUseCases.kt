package com.sakarrobotics.c40agent.domain.usecase

import com.sakarrobotics.c40agent.domain.model.ConsumableItem
import com.sakarrobotics.c40agent.domain.repository.ConsumableRepository
import kotlinx.coroutines.flow.Flow

class ConsumableUseCases(private val repository: ConsumableRepository) {
    fun observeAll(): Flow<List<ConsumableItem>> = repository.consumables
    suspend fun recordUsage(id: String, hours: Int) = repository.recordUsageHours(id, hours)
    suspend fun markReplaced(id: String) = repository.resetUsage(id)
}
