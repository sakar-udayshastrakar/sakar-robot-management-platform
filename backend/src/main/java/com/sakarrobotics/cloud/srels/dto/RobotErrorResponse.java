package com.sakarrobotics.cloud.srels.dto;

import java.time.Instant;
import java.util.UUID;

import com.sakarrobotics.cloud.srels.RobotError;

public record RobotErrorResponse(
        UUID id,
        UUID robotId,
        String errorCode,
        String severity,
        String source,
        String message,
        String sdkApi,
        String status,
        Instant occurredAt,
        Instant resolvedAt,
        UUID resolvedBy,
        Instant createdAt) {

    public static RobotErrorResponse from(RobotError error) {
        return new RobotErrorResponse(error.getId(), error.getRobotId(), error.getErrorCode(), error.getSeverity(),
                error.getSource(), error.getMessage(), error.getSdkApi(), error.getStatus(), error.getOccurredAt(),
                error.getResolvedAt(), error.getResolvedBy(), error.getCreatedAt());
    }
}
