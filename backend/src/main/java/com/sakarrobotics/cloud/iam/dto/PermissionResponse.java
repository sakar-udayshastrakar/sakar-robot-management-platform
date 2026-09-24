package com.sakarrobotics.cloud.iam.dto;

import java.util.UUID;

import com.sakarrobotics.cloud.iam.Permission;

public record PermissionResponse(UUID id, String code, String description) {

    public static PermissionResponse from(Permission permission) {
        return new PermissionResponse(permission.getId(), permission.getCode().name(), permission.getDescription());
    }
}
