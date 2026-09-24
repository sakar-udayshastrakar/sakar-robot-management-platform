package com.sakarrobotics.cloud.iam.dto;

import java.time.Instant;
import java.util.UUID;

import com.sakarrobotics.cloud.iam.Department;

public record DepartmentResponse(UUID id, String name, Instant createdAt, Instant updatedAt) {

    public static DepartmentResponse from(Department department) {
        return new DepartmentResponse(department.getId(), department.getName(), department.getCreatedAt(),
                department.getUpdatedAt());
    }
}
