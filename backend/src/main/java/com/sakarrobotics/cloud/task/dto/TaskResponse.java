package com.sakarrobotics.cloud.task.dto;

import java.time.Instant;
import java.util.UUID;

import com.sakarrobotics.cloud.task.RobotTask;

public record TaskResponse(
        UUID id,
        UUID robotId,
        UUID organizationId,
        UUID createdBy,
        String taskType,
        String parameters,
        String status,
        Instant createdAt,
        Instant updatedAt) {

    public static TaskResponse from(RobotTask task) {
        return new TaskResponse(task.getId(), task.getRobotId(), task.getOrganizationId(), task.getCreatedBy(),
                task.getTaskType(), task.getParameters(), task.getStatus(), task.getCreatedAt(), task.getUpdatedAt());
    }
}
