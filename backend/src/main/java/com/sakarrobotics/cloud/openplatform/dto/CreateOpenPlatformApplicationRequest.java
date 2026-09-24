package com.sakarrobotics.cloud.openplatform.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateOpenPlatformApplicationRequest(
        @NotNull UUID organizationId,
        @NotBlank String applicationName,
        String businessType) {
}
