package com.sakarrobotics.cloud.iot.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreatePhoneDeviceRequest(
        @NotNull UUID organizationId,
        @NotNull UUID siteId,
        @NotBlank String deviceId,
        String deviceName,
        String networkingMode) {
}
