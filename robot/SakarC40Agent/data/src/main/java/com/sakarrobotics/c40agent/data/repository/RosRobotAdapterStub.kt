package com.sakarrobotics.c40agent.data.repository

import com.sakarrobotics.c40agent.domain.model.RobotCapabilityResult
import com.sakarrobotics.c40agent.domain.model.RobotCapabilityStatus
import com.sakarrobotics.c40agent.domain.model.RosCapabilityDescriptor
import com.sakarrobotics.c40agent.domain.model.RosConnectionState
import com.sakarrobotics.c40agent.domain.model.RosEndpointConfig
import com.sakarrobotics.c40agent.domain.repository.RosRobotAdapter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The only [RosRobotAdapter] implementation as of this architecture-preparation phase. It never
 * connects to anything real: no ROS/Robot Computer endpoint has been physically confirmed yet
 * (docs/engineering/03_HARDWARE_ARCHITECTURE.md §2.1/§6, docs/engineering/02_EVIDENCE_REGISTER.md
 * E-060 - the "ARM IPC" enclosure is only a candidate, not a confirmed identification). This class
 * deliberately:
 *  - never hardcodes 192.168.64.20/9090/9091 (or any other endpoint) as a fallback - a caller must
 *    supply a [RosEndpointConfig] with a non-null [RosEndpointConfig.host], and even then this stub
 *    still reports [RobotCapabilityStatus.NOT_VERIFIED] rather than attempting a real connection,
 *    since no real ROS wire protocol is implemented here yet;
 *  - never opens a socket, never imports a WebSocket/rosbridge client library;
 *  - never returns a guessed capability list from [discoverCapabilities].
 *
 * Replacing this class (not extending [RosRobotAdapter] itself) is the intended integration point
 * once a real ROS/Robot Computer endpoint and protocol are physically confirmed.
 */
class RosRobotAdapterStub : RosRobotAdapter {

    private val _connectionState = MutableStateFlow(RosConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<RosConnectionState> = _connectionState.asStateFlow()

    override suspend fun connect(config: RosEndpointConfig): RobotCapabilityResult<Unit> {
        if (config.host.isNullOrBlank()) {
            return RobotCapabilityResult.Unavailable(
                status = RobotCapabilityStatus.NOT_VERIFIED,
                reason = "No ROS/Robot Computer endpoint configured. Historical addresses " +
                    "(192.168.64.20:9090/9091) are reverse-engineering references only, not a " +
                    "confirmed current endpoint - see docs/engineering/04_SLAM_INVESTIGATION_STATUS.md §4."
            )
        }
        // Deliberately does not attempt a real connection even when a host is supplied: no ROS
        // wire protocol is implemented in this architecture-preparation phase. A real
        // implementation replaces this whole class.
        return RobotCapabilityResult.Unavailable(
            status = RobotCapabilityStatus.NOT_VERIFIED,
            reason = "ROS/Robot Computer integration is not implemented yet - this is a stub " +
                "adapter for architecture preparation only. See " +
                "docs/architecture/SAKAR_ROBOT_SOFTWARE_ARCHITECTURE.md §4."
        )
    }

    override suspend fun disconnect() {
        _connectionState.value = RosConnectionState.DISCONNECTED
    }

    override suspend fun discoverCapabilities(): RobotCapabilityResult<List<RosCapabilityDescriptor>> =
        RobotCapabilityResult.Unavailable(
            status = RobotCapabilityStatus.NOT_VERIFIED,
            reason = "No ROS connection has ever been established - nothing to discover yet."
        )
}
