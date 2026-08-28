package com.sakarrobotics.cloud.robot.registry;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.sakarrobotics.cloud.common.error.ApiException;
import com.sakarrobotics.cloud.common.error.SakarErrorCode;

import lombok.RequiredArgsConstructor;

/**
 * The server-side gate behind Master Requirements §6.A's rule: "if a
 * robot model's capability flags do not include a capability a client
 * requests, the Robot Command Service returns UNSUPPORTED_CAPABILITY, and
 * the Web/Mobile UI must not render the corresponding control for that
 * robot at all." Every code path that is about to act on a robot's
 * capability — a controller, a future command dispatcher — must call
 * {@link #assertSupported} first.
 */
@Service
@RequiredArgsConstructor
public class RobotCapabilityService {

    private final RobotCapabilityRepository robotCapabilityRepository;

    public List<RobotCapabilityType> supportedCapabilities(UUID robotModelId) {
        return robotCapabilityRepository.findByRobotModelId(robotModelId).stream()
                .filter(RobotCapability::isSupported)
                .map(RobotCapability::getCapability)
                .toList();
    }

    public boolean isSupported(UUID robotModelId, RobotCapabilityType capability) {
        return robotCapabilityRepository.findByRobotModelIdAndCapability(robotModelId, capability)
                .map(RobotCapability::isSupported)
                .orElse(false);
    }

    public void assertSupported(UUID robotModelId, RobotCapabilityType capability) {
        if (!isSupported(robotModelId, capability)) {
            throw new ApiException(SakarErrorCode.UNSUPPORTED_CAPABILITY,
                    "This robot model does not support " + capability);
        }
    }
}
