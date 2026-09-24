package com.sakarrobotics.cloud.remotedeployment.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public record CreateRemoteDeploymentRequest(@NotNull UUID robotId, String notes) {
}
