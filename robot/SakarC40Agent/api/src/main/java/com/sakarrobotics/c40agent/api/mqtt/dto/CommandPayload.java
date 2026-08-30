package com.sakarrobotics.c40agent.api.mqtt.dto;

import java.util.Map;

/**
 * The entire inbound JSON message on the {@code commands} topic
 * (cloud-authored, Roadmap Phase 6/7 "Robot Agent Command Loop").
 *
 * <p>Unlike every other payload in this package, this is <strong>not</strong>
 * nested inside a generic {@link com.sakarrobotics.c40agent.api.mqtt.MqttEnvelope}
 * — the Sakar Cloud backend's {@code RobotCommandService.buildEnvelope()}
 * emits a flat structure (no separate "payload" wrapper, and no
 * {@code agentId}/{@code sequence} fields, since those only make sense for
 * agent-authored messages), so this class mirrors that flat shape
 * field-for-field via plain Gson field-name matching rather than being
 * parsed out of an {@code MqttEnvelope.getPayload()}. Field names must
 * match {@code RobotCommandService.buildEnvelope()} exactly; changing
 * either side without the other will silently break command delivery.
 */
public final class CommandPayload {

    private final String schemaVersion;
    private final String messageId;
    private final String robotId;
    private final String timestamp;
    private final String messageType;
    private final String commandType;
    private final String nonce;
    private final String expiresAt;
    private final Map<String, Object> params;

    public CommandPayload(String schemaVersion, String messageId, String robotId, String timestamp,
            String messageType, String commandType, String nonce, String expiresAt, Map<String, Object> params) {
        this.schemaVersion = schemaVersion;
        this.messageId = messageId;
        this.robotId = robotId;
        this.timestamp = timestamp;
        this.messageType = messageType;
        this.commandType = commandType;
        this.nonce = nonce;
        this.expiresAt = expiresAt;
        this.params = params;
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

    public String getTimestamp() {
        return timestamp;
    }

    public String getMessageType() {
        return messageType;
    }

    public String getCommandType() {
        return commandType;
    }

    public String getNonce() {
        return nonce;
    }

    public String getExpiresAt() {
        return expiresAt;
    }

    public Map<String, Object> getParams() {
        return params;
    }

    /**
     * The correlation id to use when reporting a result back to the
     * backend — equals {@code RobotCommand.id} there (the backend sets
     * {@code envelope.messageId = command.getId().toString()}, it does not
     * mint an unrelated message id for command dispatch).
     */
    public String getCommandId() {
        return messageId;
    }
}
