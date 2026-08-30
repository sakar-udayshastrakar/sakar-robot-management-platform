package com.sakarrobotics.cloud.alert.dto;

import java.time.Instant;
import java.util.UUID;

import com.sakarrobotics.cloud.alert.RobotAlert;

public record AlertResponse(
        UUID id,
        UUID robotId,
        UUID organizationId,
        String alertType,
        String severity,
        String message,
        String status,
        UUID acknowledgedBy,
        Instant acknowledgedAt,
        Instant createdAt) {

    public static AlertResponse from(RobotAlert alert) {
        return new AlertResponse(alert.getId(), alert.getRobotId(), alert.getOrganizationId(), alert.getAlertType(),
                alert.getSeverity(), alert.getMessage(), alert.getStatus(), alert.getAcknowledgedBy(),
                alert.getAcknowledgedAt(), alert.getCreatedAt());
    }
}
