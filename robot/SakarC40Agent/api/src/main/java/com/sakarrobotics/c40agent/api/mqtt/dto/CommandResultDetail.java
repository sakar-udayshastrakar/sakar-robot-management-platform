package com.sakarrobotics.c40agent.api.mqtt.dto;

/**
 * Serialized (via Gson, as the raw {@code payload} string of an {@code
 * eventType="COMMAND_RESULT"} {@link EventPayload}) to report a command
 * lifecycle transition back to the Sakar Cloud backend. Field names must
 * match the backend's {@code CommandResultDetail} record exactly.
 *
 * <p>This deliberately reuses the existing EVENT channel/topic/publish
 * method ({@code AgentMqttClient.publishEvent}) rather than adding a new
 * MQTT message type or topic — see the backend record's Javadoc for the
 * full rationale. {@code status} is one of {@code RECEIVED, EXECUTING,
 * COMPLETED, FAILED, TIMEOUT} — see {@link
 * com.sakarrobotics.c40agent.api.mqtt.CommandDispatcher} for exactly when
 * each is published.
 */
public final class CommandResultDetail {

    private final String commandId;
    private final String status;
    private final String detail;
    private final Long durationMs;

    public CommandResultDetail(String commandId, String status, String detail, Long durationMs) {
        this.commandId = commandId;
        this.status = status;
        this.detail = detail;
        this.durationMs = durationMs;
    }

    public String getCommandId() {
        return commandId;
    }

    public String getStatus() {
        return status;
    }

    public String getDetail() {
        return detail;
    }

    public Long getDurationMs() {
        return durationMs;
    }
}
