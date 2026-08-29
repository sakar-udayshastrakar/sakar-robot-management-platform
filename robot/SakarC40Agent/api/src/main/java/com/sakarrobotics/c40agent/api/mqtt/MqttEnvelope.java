package com.sakarrobotics.c40agent.api.mqtt;

import com.google.gson.JsonObject;

/**
 * Mirrors the Sakar Cloud backend's {@code MqttEnvelope} record field-for-
 * field (Phase 3 Part 3). {@code timestamp} is a plain ISO-8601 string
 * (e.g. {@code java.time.Instant.now().toString()}) rather than a
 * dedicated date type - Gson has no built-in {@code java.time} adapter,
 * and Jackson's default {@code Instant} deserializer (used backend-side)
 * accepts this format natively, so no custom adapter is needed on either
 * side.
 */
public final class MqttEnvelope {

    private final String schemaVersion;
    private final String messageId;
    private final String robotId;
    private final String agentId;
    private final String timestamp;
    private final String messageType;
    private final long sequence;
    private final JsonObject payload;

    public MqttEnvelope(String schemaVersion, String messageId, String robotId, String agentId, String timestamp,
                         MqttMessageType messageType, long sequence, JsonObject payload) {
        this.schemaVersion = schemaVersion;
        this.messageId = messageId;
        this.robotId = robotId;
        this.agentId = agentId;
        this.timestamp = timestamp;
        this.messageType = messageType.name();
        this.sequence = sequence;
        this.payload = payload;
    }

    public String getSchemaVersion() {
        return schemaVersion;
    }

    public String getMessageId() {
        return messageId;
    }

    public String getRobotId() {
        return robotId;
    }

    public String getAgentId() {
        return agentId;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public String getMessageType() {
        return messageType;
    }

    public long getSequence() {
        return sequence;
    }

    public JsonObject getPayload() {
        return payload;
    }
}
