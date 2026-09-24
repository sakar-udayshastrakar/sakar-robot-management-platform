package com.sakarrobotics.cloud.iam.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;

/**
 * Edits the small set of fields this batch's UI actually needs to change —
 * email/password/role/status all already have their own dedicated endpoints
 * ({@code POST /{id}/role}, {@code /suspend}, {@code /activate}) and are not
 * duplicated here.
 */
public record UpdateUserRequest(@NotBlank String fullName, UUID departmentId) {
}
