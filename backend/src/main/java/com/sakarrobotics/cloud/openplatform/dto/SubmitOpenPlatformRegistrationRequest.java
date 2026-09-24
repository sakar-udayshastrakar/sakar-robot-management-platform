package com.sakarrobotics.cloud.openplatform.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SubmitOpenPlatformRegistrationRequest(
        @NotNull UUID organizationId,
        @NotBlank String companyName,
        String area,
        String companyAddress,
        String systemMatcher,
        String contactInformation,
        String dockingRequirements) {
}
