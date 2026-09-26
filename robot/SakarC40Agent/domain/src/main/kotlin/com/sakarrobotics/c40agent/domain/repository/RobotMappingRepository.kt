package com.sakarrobotics.c40agent.domain.repository

import com.sakarrobotics.c40agent.domain.model.MappingSessionState
import com.sakarrobotics.c40agent.domain.model.RobotCapabilityResult
import com.sakarrobotics.c40agent.domain.model.RobotMapMetadata
import com.sakarrobotics.c40agent.domain.model.RobotPose
import kotlinx.coroutines.flow.Flow

/**
 * Live mapping/SLAM session control - distinct from the existing, already-real [RobotMapRepository]
 * (file-level map list/download/deploy via the public Peanut SDK). None of this has a confirmed SDK
 * or ROS API yet (docs/engineering/04_SLAM_INVESTIGATION_STATUS.md, docs/engineering/
 * 09_KEENON_OFFICIAL_C40_DOCUMENTATION_AUDIT.md §5) - every method here returns
 * [RobotCapabilityResult.Unavailable] until a real ROS/SLAM source is identified and confirmed.
 * No implementation of this interface may return [RobotCapabilityResult.Available] with a
 * fabricated value - see this task's own "never return simulated values from a REAL
 * implementation" / "never make these functions return fake success" instructions.
 */
interface RobotMappingRepository {
    val mappingStatus: Flow<RobotCapabilityResult<MappingSessionState>>

    suspend fun startMapping(): RobotCapabilityResult<Unit>
    suspend fun stopMapping(): RobotCapabilityResult<Unit>
    suspend fun saveMap(name: String): RobotCapabilityResult<Unit>
    suspend fun loadMap(mapId: String): RobotCapabilityResult<Unit>
    suspend fun localize(): RobotCapabilityResult<Unit>
    suspend fun currentPose(): RobotCapabilityResult<RobotPose>
    suspend fun mapMetadata(mapId: String): RobotCapabilityResult<RobotMapMetadata>
}
