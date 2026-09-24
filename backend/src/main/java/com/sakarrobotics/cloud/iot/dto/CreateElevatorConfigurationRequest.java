package com.sakarrobotics.cloud.iot.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateElevatorConfigurationRequest(
        @NotNull UUID organizationId,
        @NotNull UUID siteId,
        @NotNull UUID elevatorDeviceId,
        @NotNull UUID robotId,
        @NotBlank String name,
        String notes) {
}
