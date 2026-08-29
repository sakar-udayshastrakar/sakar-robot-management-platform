package com.sakarrobotics.cloud.robot.registry.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Returned exactly once, at provisioning/rotation time (Phase 3 Part 4/5) —
 * never persisted or retrievable in plaintext again afterward (only its
 * BCrypt hash lives in {@code robot_credentials}). Mirrors {@code
 * SAKAR_ROBOT_PLATFORM_API_SPEC.md} §2.1's {@code robot_credentials:
 * {credential_type, credential_value}} Register-response shape.
 */
public record RobotMqttCredentialResponse(UUID robotId, String mqttUsername, String mqttPassword, Instant issuedAt) {
}
