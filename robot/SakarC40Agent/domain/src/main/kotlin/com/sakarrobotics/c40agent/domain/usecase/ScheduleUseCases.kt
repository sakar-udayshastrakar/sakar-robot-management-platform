package com.sakarrobotics.c40agent.domain.usecase

import com.sakarrobotics.c40agent.domain.model.ScheduleTask
import com.sakarrobotics.c40agent.domain.repository.ScheduleRepository
import java.util.UUID
import kotlinx.coroutines.flow.Flow

class ScheduleUseCases(private val repository: ScheduleRepository) {

    fun observeSchedules(): Flow<List<ScheduleTask>> = repository.schedules

    suspend fun create(task: ScheduleTask): ScheduleTask {
        val withId = if (task.id.isBlank()) task.copy(id = UUID.randomUUID().toString()) else task
        repository.upsert(withId)
        return withId
    }

    suspend fun update(task: ScheduleTask) = repository.upsert(task)

    suspend fun delete(taskId: String) = repository.delete(taskId)

    suspend fun setEnabled(taskId: String, enabled: Boolean) = repository.setEnabled(taskId, enabled)
}
