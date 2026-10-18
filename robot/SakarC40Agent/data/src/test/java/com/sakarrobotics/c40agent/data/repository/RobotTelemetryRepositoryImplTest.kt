package com.sakarrobotics.c40agent.data.repository

import com.sakarrobotics.c40agent.domain.model.BatteryState
import com.sakarrobotics.c40agent.domain.model.Capability
import com.sakarrobotics.c40agent.domain.model.CloudSyncState
import com.sakarrobotics.c40agent.domain.model.RobotCapabilityResult
import com.sakarrobotics.c40agent.domain.model.RobotCapabilityStatus
import com.sakarrobotics.c40agent.domain.model.RobotLinkState
import com.sakarrobotics.c40agent.domain.repository.RobotBatteryRepository
import com.sakarrobotics.c40agent.domain.repository.RobotConnectionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RobotTelemetryRepositoryImplTest {

    private class FakeConnection : RobotConnectionRepository {
        override val linkState: Flow<RobotLinkState> = flowOf(RobotLinkState.CONNECTED)
        override val lastCommunicationMillis: Flow<Long?> = flowOf(1L)
        override val cloudSyncState: Flow<CloudSyncState> = flowOf(CloudSyncState.OFFLINE)
        override suspend fun connect(): Result<Unit> = Result.success(Unit)
        override fun disconnect() = Unit
    }

    private class FakeBattery(state: BatteryState) : RobotBatteryRepository {
        override val battery: Flow<BatteryState> = flowOf(state)
        override suspend fun refresh(): BatteryState = error("not used")
    }

    private fun snapshotFor(capability: Capability, percentage: Int? = null) =
        RobotTelemetryRepositoryImpl(
            FakeConnection(),
            FakeBattery(BatteryState(percentage, null, null, null, capability))
        ).snapshot

    @Test
    fun `unverified battery makes telemetry not verified instead of real`() = runTest {
        val result = snapshotFor(Capability.UNKNOWN).first()
        val unavailable = result as RobotCapabilityResult.Unavailable
        assertEquals(RobotCapabilityStatus.NOT_VERIFIED, unavailable.status)
    }

    @Test
    fun `failed battery query makes telemetry not available`() = runTest {
        val result = snapshotFor(Capability.UNAVAILABLE).first()
        val unavailable = result as RobotCapabilityResult.Unavailable
        assertEquals(RobotCapabilityStatus.NOT_AVAILABLE, unavailable.status)
    }

    @Test
    fun `a real battery reading flows through with no invented charging flag`() = runTest {
        val result = snapshotFor(Capability.REAL, percentage = 61).first()
        val available = result as RobotCapabilityResult.Available
        assertEquals(RobotCapabilityStatus.REAL, available.status)
        assertEquals(61, available.value.batteryPercentage)
        assertNull(available.value.isCharging)
        assertTrue(available.value.linkState == RobotLinkState.CONNECTED)
    }
}
