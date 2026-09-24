package com.sakarrobotics.cloud.iam.dto;

import java.time.Instant;
import java.util.UUID;

import com.sakarrobotics.cloud.iam.User;

/** Never carries {@code passwordHash}/{@code mfaSecret} — those never leave the service layer. */
public record UserResponse(
        UUID id,
        UUID organizationId,
        String email,
        String fullName,
        String roleName,
        String status,
        boolean mfaEnabled,
        Instant lastLoginAt,
        Instant createdAt,
        String userType,
        UUID departmentId,
        String departmentName) {

    /**
     * @param departmentName resolved by the caller (a single batch lookup for a list, not a
     *                        per-row query) — {@code null} when {@code departmentId} is null.
     */
    public static UserResponse from(User user, String departmentName) {
        return new UserResponse(user.getId(), user.getOrganizationId(), user.getEmail(), user.getFullName(),
                user.getRole().getName().name(), user.getStatus().name(), user.isMfaEnabled(),
                user.getLastLoginAt(), user.getCreatedAt(), user.getUserType().name(), user.getDepartmentId(),
                departmentName);
    }
}
