package com.sakarrobotics.cloud.org.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateSiteRequest(
        @NotNull UUID organizationId,
        @NotBlank String name,
        String address,
        String timezone) {
}
