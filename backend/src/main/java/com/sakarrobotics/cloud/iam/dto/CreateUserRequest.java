package com.sakarrobotics.cloud.iam.dto;

import java.util.UUID;

import com.sakarrobotics.cloud.iam.RoleName;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * {@code organizationId} is nullable only for a SUPER_ADMIN-created,
 * cross-organization Sakar staff account — {@link
 * com.sakarrobotics.cloud.iam.UserController} enforces that, not this DTO.
 */
public record CreateUserRequest(
        UUID organizationId,
        @NotBlank @Email String email,
        @NotBlank @Size(min = 12, message = "Password must be at least 12 characters") String password,
        @NotBlank String fullName,
        @NotNull RoleName roleName) {
}
