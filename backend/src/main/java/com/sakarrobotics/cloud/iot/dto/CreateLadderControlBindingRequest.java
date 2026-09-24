package com.sakarrobotics.cloud.iot.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateLadderControlBindingRequest(
        @NotNull UUID organizationId,
        @NotNull UUID siteId,
        @NotBlank String manufacturer,
        String buildingId,
        String clientId) {
}
