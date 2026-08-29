package com.sakarrobotics.c40agent.api.mqtt.dto;

/** Cloud-authored, agent-parsed only - never published by the agent. */
public final class AckPayload {

    private final String messageId;
    private final String robotId;
    private final boolean accepted;
    private final String timestamp;
    private final String reason;

    public AckPayload(String messageId, String robotId, boolean accepted, String timestamp, String reason) {
        this.messageId = messageId;
        this.robotId = robotId;
        this.accepted = accepted;
        this.timestamp = timestamp;
        this.reason = reason;
    }

    public String getMessageId() {
        return messageId;
    }

    public String getRobotId() {
        return robotId;
    }

    public boolean isAccepted() {
        return accepted;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public String getReason() {
        return reason;
    }
}
