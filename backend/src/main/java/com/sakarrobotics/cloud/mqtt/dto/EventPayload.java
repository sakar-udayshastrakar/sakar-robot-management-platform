package com.sakarrobotics.cloud.mqtt.dto;

import java.time.Instant;

/** {@code messageType: EVENT} payload — maps to {@code robot_events}. */
public record EventPayload(String eventType, String severity, String payload, Instant occurredAt) {
}
