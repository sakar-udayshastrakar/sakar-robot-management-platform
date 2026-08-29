package com.sakarrobotics.c40agent.api.mqtt.dto;

public final class EventPayload {

    private final String eventType;
    private final String severity;
    private final String payload;
    private final String occurredAt;

    public EventPayload(String eventType, String severity, String payload, String occurredAt) {
        this.eventType = eventType;
        this.severity = severity;
        this.payload = payload;
        this.occurredAt = occurredAt;
    }

    public String getEventType() {
        return eventType;
    }

    public String getSeverity() {
        return severity;
    }

    public String getPayload() {
        return payload;
    }

    public String getOccurredAt() {
        return occurredAt;
    }
}
