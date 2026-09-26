package com.sakarrobotics.c40agent.data.repository

import com.sakarrobotics.c40agent.domain.model.MappingSessionState
import com.sakarrobotics.c40agent.domain.model.RobotCapabilityResult
import com.sakarrobotics.c40agent.domain.model.RobotCapabilityStatus
import com.sakarrobotics.c40agent.domain.model.RobotMapMetadata
import com.sakarrobotics.c40agent.domain.model.RobotPose
import com.sakarrobotics.c40agent.domain.repository.RobotMappingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The only [RobotMappingRepository] implementation as of this architecture-preparation phase.
 * Every method returns [RobotCapabilityResult.Unavailable] - live mapping/SLAM control has no
 * confirmed Peanut SDK or ROS API (docs/engineering/04_SLAM_INVESTIGATION_STATUS.md,
 * docs/engineering/09_KEENON_OFFICIAL_C40_DOCUMENTATION_AUDIT.md §5). This class must never be
 * changed to return [RobotCapabilityResult.Available] with a fabricated [RobotPose]/
 * [RobotMapMetadata] - that would be exactly the "fake success" this architecture phase's own
 * instructions forbid. Replacing this class is the intended integration point once a real
 * ROS/SLAM source is confirmed.
 */
class NotAvailableMappingRepository : RobotMappingRepository {

    private companion object {
        const val REASON = "Live mapping/SLAM is not exposed by the licensed Peanut SDK and no " +
            "ROS/Robot Computer connection is confirmed yet - see " +
            "docs/engineering/04_SLAM_INVESTIGATION_STATUS.md."
    }

    private val _mappingStatus = MutableStateFlow<RobotCapabilityResult<MappingSessionState>>(
        RobotCapabilityResult.Unavailable(RobotCapabilityStatus.NOT_AVAILABLE, REASON)
    )
    override val mappingStatus: StateFlow<RobotCapabilityResult<MappingSessionState>> = _mappingStatus.asStateFlow()

    override suspend fun startMapping(): RobotCapabilityResult<Unit> =
        RobotCapabilityResult.Unavailable(RobotCapabilityStatus.NOT_AVAILABLE, REASON)

    override suspend fun stopMapping(): RobotCapabilityResult<Unit> =
        RobotCapabilityResult.Unavailable(RobotCapabilityStatus.NOT_AVAILABLE, REASON)

    override suspend fun saveMap(name: String): RobotCapabilityResult<Unit> =
        RobotCapabilityResult.Unavailable(RobotCapabilityStatus.NOT_AVAILABLE, REASON)

    override suspend fun loadMap(mapId: String): RobotCapabilityResult<Unit> =
        RobotCapabilityResult.Unavailable(RobotCapabilityStatus.NOT_AVAILABLE, REASON)

    override suspend fun localize(): RobotCapabilityResult<Unit> =
        RobotCapabilityResult.Unavailable(RobotCapabilityStatus.NOT_AVAILABLE, REASON)

    override suspend fun currentPose(): RobotCapabilityResult<RobotPose> =
        RobotCapabilityResult.Unavailable(RobotCapabilityStatus.NOT_AVAILABLE, REASON)

    override suspend fun mapMetadata(mapId: String): RobotCapabilityResult<RobotMapMetadata> =
        RobotCapabilityResult.Unavailable(RobotCapabilityStatus.NOT_AVAILABLE, REASON)
}
