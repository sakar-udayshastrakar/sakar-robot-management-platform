package com.sakarrobotics.cloud.iam.dto;

import com.sakarrobotics.cloud.iam.RoleName;

import jakarta.validation.constraints.NotNull;

public record ChangeUserRoleRequest(@NotNull RoleName roleName) {
}
