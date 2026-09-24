package com.sakarrobotics.cloud.resourceconfig.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateResourceSceneRequest(
        @NotNull UUID organizationId,
        UUID siteId,
        UUID robotId,
        @NotBlank String name,
        String resourcePackType) {
}
