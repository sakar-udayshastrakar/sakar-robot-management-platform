package com.sakarrobotics.c40agent.domain.repository

import com.sakarrobotics.c40agent.domain.model.RobotCapabilityResult
import com.sakarrobotics.c40agent.domain.model.RosCapabilityDescriptor
import com.sakarrobotics.c40agent.domain.model.RosConnectionState
import com.sakarrobotics.c40agent.domain.model.RosEndpointConfig
import kotlinx.coroutines.flow.Flow

/**
 * The future seam for the real ROS/Robot Computer (docs/engineering/03_HARDWARE_ARCHITECTURE.md
 * §2/§2.1/§6) - deliberately minimal for this architecture-preparation phase. It supports only
 * connection state, connect/disconnect lifecycle, and capability discovery, exactly as scoped by
 * this task. It must NEVER grow a guessed ROS topic/service/action method: when the real topology
 * is confirmed (docs/engineering/02_EVIDENCE_REGISTER.md E-052, E-058 through E-060), extend this
 * interface then, backed by real evidence, not now.
 *
 * The only implementation as of this phase, [com.sakarrobotics.c40agent.data.repository.RosRobotAdapterStub]
 * (:data), always reports [RosConnectionState.DISCONNECTED] and [RobotCapabilityResult.Unavailable] -
 * this is intentional, not a bug: no real ROS endpoint has been confirmed yet.
 */
interface RosRobotAdapter {
    val connectionState: Flow<RosConnectionState>

    /**
     * [config] must come from external configuration (settings/remote-config), never a hardcoded
     * default - see [RosEndpointConfig]'s own KDoc for why 192.168.64.20/9090/9091 must never
     * appear as a fallback here.
     */
    suspend fun connect(config: RosEndpointConfig): RobotCapabilityResult<Unit>

    suspend fun disconnect()

    /** Never returns a guessed/assumed capability list - only what a real connection has confirmed. */
    suspend fun discoverCapabilities(): RobotCapabilityResult<List<RosCapabilityDescriptor>>
}
