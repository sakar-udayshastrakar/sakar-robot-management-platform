package com.sakarrobotics.cloud.iot.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateElevatorDeviceRequest(
        @NotNull UUID organizationId,
        @NotNull UUID siteId,
        @NotBlank String deviceId,
        String deviceName,
        String building,
        String protocol,
        String networkingMode,
        String communicationMode) {
}
