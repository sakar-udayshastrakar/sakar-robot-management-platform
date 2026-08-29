package com.sakarrobotics.cloud.mqtt.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Published by the backend to {@code .../ack} after processing any inbound
 * message (Phase 3 Part 11). {@code reason} is always a stable, generic
 * {@link com.sakarrobotics.cloud.common.error.SakarErrorCode}-style code —
 * never a raw exception message or stack trace (Phase 3 Part 11: "Never
 * expose sensitive internal error details to the robot").
 */
public record AckPayload(String messageId, UUID robotId, boolean accepted, Instant timestamp, String reason) {

    public static AckPayload accepted(String messageId, UUID robotId) {
        return new AckPayload(messageId, robotId, true, Instant.now(), null);
    }

    public static AckPayload rejected(String messageId, UUID robotId, String reason) {
        return new AckPayload(messageId, robotId, false, Instant.now(), reason);
    }
}
