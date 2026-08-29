package com.sakarrobotics.cloud.mqtt;

import java.time.Instant;
import java.util.UUID;

import tools.jackson.databind.JsonNode;

/**
 * The versioned Agent&lt;-&gt;Cloud MQTT message envelope (Phase 3 topic
 * design Part 3). {@code payload}'s shape depends on {@code messageType} —
 * kept as a raw {@link JsonNode} here (same "don't invent a typed schema
 * for what hasn't been confirmed" convention {@code KeenonApiClient}
 * already uses) and converted to the specific payload record for that type
 * only inside {@link MqttInboundMessageService}, after the envelope itself
 * has passed structural validation.
 *
 * <p>{@code agentId} is recorded for observability/traceability only —
 * Phase 3 does not maintain a separate {@code agents} identity table, so
 * it is never itself an authorization input; {@code robotId} (validated
 * against the registered {@link com.sakarrobotics.cloud.robot.registry.Robot})
 * is the sole identity the backend trusts.
 */
public record MqttEnvelope(
        String schemaVersion,
        String messageId,
        UUID robotId,
        String agentId,
        Instant timestamp,
        MqttMessageType messageType,
        long sequence,
        JsonNode payload) {
}
