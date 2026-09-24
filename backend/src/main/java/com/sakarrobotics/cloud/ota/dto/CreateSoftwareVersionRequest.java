package com.sakarrobotics.cloud.ota.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateSoftwareVersionRequest(
        @NotNull UUID organizationId,
        @NotBlank String packageName,
        String wholeMachineSoftware,
        @NotBlank String packageVersion,
        String hardwareVersion,
        Boolean grayscale,
        Long sizeBytes,
        String notes) {
}
