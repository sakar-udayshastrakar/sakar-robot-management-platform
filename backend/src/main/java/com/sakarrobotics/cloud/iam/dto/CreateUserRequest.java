package com.sakarrobotics.cloud.iam.dto;

import java.util.UUID;

import com.sakarrobotics.cloud.iam.RoleName;
import com.sakarrobotics.cloud.iam.UserType;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * {@code organizationId} is nullable only for a SUPER_ADMIN-created,
 * cross-organization Sakar staff account — {@link
 * com.sakarrobotics.cloud.iam.UserController} enforces that, not this DTO.
 *
 * <p>{@code departmentId} is only meaningful for {@code userType ==
 * INTERNAL}; a value for an {@code EXTERNAL} user is rejected by {@link
 * com.sakarrobotics.cloud.iam.UserService#create}, not silently ignored.
 *
 * <p>{@code userType} is nullable, not {@code @NotNull} — a caller that
 * predates this field (every existing test/integration) still works
 * unchanged; {@link com.sakarrobotics.cloud.iam.UserController} defaults a
 * missing value to {@code EXTERNAL}, matching V19's own DB-column default.
 */
public record CreateUserRequest(
        UUID organizationId,
        @NotBlank @Email String email,
        @NotBlank @Size(min = 12, message = "Password must be at least 12 characters") String password,
        @NotBlank String fullName,
        @NotNull RoleName roleName,
        UserType userType,
        UUID departmentId) {
}
