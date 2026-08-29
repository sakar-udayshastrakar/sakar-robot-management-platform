package com.sakarrobotics.cloud.mqtt.dto;

import java.time.Instant;

/** {@code messageType: ERROR} payload — maps to {@code robot_errors}. */
public record ErrorPayload(
        String errorCode,
        String severity,
        String source,
        String message,
        String sdkApi,
        Instant occurredAt) {
}
