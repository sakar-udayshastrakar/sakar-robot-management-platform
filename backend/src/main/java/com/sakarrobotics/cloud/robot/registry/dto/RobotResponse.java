package com.sakarrobotics.cloud.robot.registry.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.sakarrobotics.cloud.robot.registry.Robot;
import com.sakarrobotics.cloud.robot.registry.RobotCapabilityType;
import com.sakarrobotics.cloud.robot.registry.RobotLifecycleStatus;
import com.sakarrobotics.cloud.telemetry.RobotConnectionStatus;
import com.sakarrobotics.cloud.telemetry.RobotStatus;

/**
 * The public shape of a robot. Deliberately excludes {@code externalRobotId}
 * (the vendor identifier) — external clients see only Sakar identifiers
 * (Master Requirements "Keenon Cloud Integration": "do not expose vendor
 * naming to external clients").
 *
 * <p>{@code connectionStatus} is the backend's single authoritative
 * connectivity verdict (see {@code RobotConnectivityService}), carried on
 * the robot resource itself so the list and the detail page render the
 * same value without either one recomputing it. {@code lastSeenAt} is the
 * UTC instant that verdict was derived from, for display only — clients
 * must not re-derive status from it.
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
        RobotConnectionStatus connectionStatus,
        Instant lastSeenAt,
        Instant createdAt) {

    /**
     * @param robotStatus the robot's {@code robot_status} row, or {@code null} if it
     *                    has never reported — never a fabricated stand-in row.
     */
    public static RobotResponse from(Robot robot, List<RobotCapabilityType> capabilities,
            RobotConnectionStatus connectionStatus, RobotStatus robotStatus) {
        return new RobotResponse(
                robot.getId(), robot.getOrganizationId(), robot.getSiteId(), robot.getRobotModelId(),
                robot.getName(), robot.getSerialNumber(), robot.getStatus(), capabilities,
                connectionStatus, robotStatus != null ? robotStatus.getLastSeenAt() : null,
                robot.getCreatedAt());
    }
}
