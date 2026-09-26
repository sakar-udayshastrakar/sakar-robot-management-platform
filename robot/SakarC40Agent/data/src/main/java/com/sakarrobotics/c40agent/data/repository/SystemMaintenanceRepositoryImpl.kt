package com.sakarrobotics.c40agent.data.repository

import com.sakarrobotics.c40agent.data.db.AppDatabase
import com.sakarrobotics.c40agent.domain.repository.AuthRepository
import com.sakarrobotics.c40agent.domain.repository.SystemMaintenanceRepository
import com.sakarrobotics.c40agent.logging.SdkCallLogger

class SystemMaintenanceRepositoryImpl(
    private val db: AppDatabase,
    private val authRepository: AuthRepository,
    private val localPreferencesRepository: com.sakarrobotics.c40agent.domain.repository.LocalPreferencesRepository
) : SystemMaintenanceRepository {

    override suspend fun factoryReset() {
        db.scheduleTaskDao().deleteAll()
        db.consumableDao().deleteAll()
        db.routeDao().deleteAll()
        db.cleaningZoneDao().deleteAll()
        authRepository.clearSuperUserPin()
        localPreferencesRepository.update { com.sakarrobotics.c40agent.domain.model.LocalPreferences() }
        SdkCallLogger.getInstance().clear()
    }
}
