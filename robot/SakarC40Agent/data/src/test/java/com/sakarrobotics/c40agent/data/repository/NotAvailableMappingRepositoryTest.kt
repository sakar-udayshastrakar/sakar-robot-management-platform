package com.sakarrobotics.c40agent.data.repository

import com.sakarrobotics.c40agent.domain.model.RobotCapabilityResult
import com.sakarrobotics.c40agent.domain.model.RobotCapabilityStatus
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Every method must return [RobotCapabilityResult.Unavailable] - this is the entire point of this
 * repository existing before a real ROS/SLAM source is confirmed (see this class's own KDoc and
 * docs/architecture/SAKAR_ROBOT_SOFTWARE_ARCHITECTURE.md §6). If a future edit ever makes one of
 * these methods return [RobotCapabilityResult.Available], that is exactly the "fake success" this
 * architecture phase's own instructions forbid - this test exists to catch that regression.
 */
class NotAvailableMappingRepositoryTest {

    private val repository = NotAvailableMappingRepository()

    @Test
    fun startMapping_returnsUnavailable() = runTest {
        assertInstanceOf(RobotCapabilityResult.Unavailable::class.java, repository.startMapping())
    }

    @Test
    fun stopMapping_returnsUnavailable() = runTest {
        assertInstanceOf(RobotCapabilityResult.Unavailable::class.java, repository.stopMapping())
    }

    @Test
    fun saveMap_returnsUnavailable() = runTest {
        assertInstanceOf(RobotCapabilityResult.Unavailable::class.java, repository.saveMap("test-map"))
    }

    @Test
    fun loadMap_returnsUnavailable() = runTest {
        assertInstanceOf(RobotCapabilityResult.Unavailable::class.java, repository.loadMap("test-map"))
    }

    @Test
    fun localize_returnsUnavailable() = runTest {
        assertInstanceOf(RobotCapabilityResult.Unavailable::class.java, repository.localize())
    }

    @Test
    fun currentPose_returnsUnavailable() = runTest {
        assertInstanceOf(RobotCapabilityResult.Unavailable::class.java, repository.currentPose())
    }

    @Test
    fun mapMetadata_returnsUnavailable() = runTest {
        assertInstanceOf(RobotCapabilityResult.Unavailable::class.java, repository.mapMetadata("test-map"))
    }

    @Test
    fun mappingStatus_initialValueIsUnavailableWithNotAvailableStatus() {
        val initial = repository.mappingStatus.value
        assertInstanceOf(RobotCapabilityResult.Unavailable::class.java, initial)
        assertEquals(RobotCapabilityStatus.NOT_AVAILABLE, (initial as RobotCapabilityResult.Unavailable).status)
    }
}
