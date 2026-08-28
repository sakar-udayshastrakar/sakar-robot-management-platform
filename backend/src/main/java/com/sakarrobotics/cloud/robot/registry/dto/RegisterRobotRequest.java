package com.sakarrobotics.cloud.robot.registry.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RegisterRobotRequest(
        @NotNull UUID organizationId,
        UUID siteId,
        @NotNull UUID robotModelId,
        @NotBlank String name,
        @NotBlank String serialNumber,
        String externalRobotId) {
}
