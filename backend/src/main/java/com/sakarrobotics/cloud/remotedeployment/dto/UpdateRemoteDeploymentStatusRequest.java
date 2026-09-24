package com.sakarrobotics.cloud.remotedeployment.dto;

import com.sakarrobotics.cloud.remotedeployment.RemoteDeploymentStatus;

import jakarta.validation.constraints.NotNull;

public record UpdateRemoteDeploymentStatusRequest(@NotNull RemoteDeploymentStatus status) {
}
