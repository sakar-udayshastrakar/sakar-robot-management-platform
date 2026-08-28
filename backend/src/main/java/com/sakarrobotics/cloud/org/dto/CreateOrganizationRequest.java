package com.sakarrobotics.cloud.org.dto;

import java.util.UUID;

import com.sakarrobotics.cloud.org.OrganizationType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateOrganizationRequest(
        @NotBlank String name,
        @NotNull OrganizationType orgType,
        UUID parentOrganizationId) {
}
