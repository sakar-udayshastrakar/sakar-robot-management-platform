package com.sakarrobotics.cloud.robot.registry.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/** "Allocate to lower level agent" — the target must be a direct child of the robot's current organization. */
public record AllocateRobotRequest(@NotNull UUID organizationId) {
}
