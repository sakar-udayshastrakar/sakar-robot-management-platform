package com.sakarrobotics.cloud.robot.registry.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.sakarrobotics.cloud.robot.registry.Robot;
import com.sakarrobotics.cloud.robot.registry.RobotCapabilityType;
import com.sakarrobotics.cloud.robot.registry.RobotLifecycleStatus;

/**
 * The public shape of a robot. Deliberately excludes {@code externalRobotId}
 * (the vendor identifier) — external clients see only Sakar identifiers
 * (Master Requirements "Keenon Cloud Integration": "do not expose vendor
 * naming to external clients").
 */
public record RobotResponse(
        UUID id,
        UUID organizationId,
        UUID siteId,
        UUID robotModelId,
        String name,
        String serialNumber,
        RobotLifecycleStatus status,
        List<RobotCapabilityType> capabilities,
        Instant createdAt) {

    public static RobotResponse from(Robot robot, List<RobotCapabilityType> capabilities) {
        return new RobotResponse(
                robot.getId(), robot.getOrganizationId(), robot.getSiteId(), robot.getRobotModelId(),
                robot.getName(), robot.getSerialNumber(), robot.getStatus(), capabilities, robot.getCreatedAt());
    }
}
