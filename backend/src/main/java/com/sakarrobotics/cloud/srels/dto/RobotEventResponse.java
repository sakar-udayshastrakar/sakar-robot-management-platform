package com.sakarrobotics.cloud.srels.dto;

import java.time.Instant;
import java.util.UUID;

import com.sakarrobotics.cloud.srels.RobotEvent;

public record RobotEventResponse(
        Long id,
        UUID robotId,
        String eventType,
        String severity,
        String payload,
        Instant occurredAt,
        Instant createdAt) {

    public static RobotEventResponse from(RobotEvent event) {
        return new RobotEventResponse(event.getId(), event.getRobotId(), event.getEventType(), event.getSeverity(),
                event.getPayload(), event.getOccurredAt(), event.getCreatedAt());
    }
}
