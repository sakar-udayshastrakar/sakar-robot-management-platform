package com.sakarrobotics.cloud.iam.dto;

import java.util.List;
import java.util.UUID;

import com.sakarrobotics.cloud.iam.Role;

/** Roles/permissions are fixed reference data (V9__seed_rbac.sql) — this is a read-only view, not an editable resource. */
public record RoleResponse(UUID id, String name, String description, List<String> permissions) {

    public static RoleResponse from(Role role) {
        List<String> permissions = role.getPermissions().stream().map(p -> p.getCode().name()).sorted().toList();
        return new RoleResponse(role.getId(), role.getName().name(), role.getDescription(), permissions);
    }
}
