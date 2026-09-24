package com.sakarrobotics.cloud.repair.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;

public record CreateRepairRequestRequest(
        UUID organizationId,
        UUID siteId,
        UUID robotId,
        @NotBlank String symptom,
        String reportedBy,
        String notes) {
}
