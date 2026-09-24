package com.sakarrobotics.cloud.iam.dto;

import java.util.Set;

import com.sakarrobotics.cloud.iam.PermissionCode;

import jakarta.validation.constraints.NotNull;

/**
 * Deliberately has no {@code name} field — {@code Role.name} is the {@code
 * RoleName} Java enum, not free text, and every {@code hasRole}/{@code
 * isSuperAdmin}-style check in this codebase switches on it. Turning it into
 * an arbitrarily-creatable value is a separate, security-critical RBAC
 * architecture change, out of scope for this endpoint (see {@link
 * com.sakarrobotics.cloud.iam.RoleController}'s Javadoc).
 */
public record UpdateRoleRequest(String description, @NotNull Set<PermissionCode> permissionCodes) {
}
