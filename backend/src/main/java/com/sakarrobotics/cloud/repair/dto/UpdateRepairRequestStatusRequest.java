package com.sakarrobotics.cloud.repair.dto;

import com.sakarrobotics.cloud.repair.RepairRequestStatus;

import jakarta.validation.constraints.NotNull;

public record UpdateRepairRequestStatusRequest(@NotNull RepairRequestStatus status, String notes) {
}
