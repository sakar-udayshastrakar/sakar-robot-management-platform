package com.sakarrobotics.cloud.mqtt;

/** The fixed set of per-robot topic suffixes (Part 2 of the Phase 3 topic design). */
public enum MqttTopicKind {
    PRESENCE("presence"),
    HEARTBEAT("heartbeat"),
    TELEMETRY("telemetry"),
    EVENTS("events"),
    ERRORS("errors"),
    ACK("ack");

    private final String segment;

    MqttTopicKind(String segment) {
        this.segment = segment;
    }

    public String segment() {
        return segment;
    }

    public static MqttTopicKind fromSegment(String segment) {
        for (MqttTopicKind kind : values()) {
            if (kind.segment.equals(segment)) {
                return kind;
            }
        }
        throw new IllegalArgumentException("Unknown MQTT topic segment: " + segment);
    }
}
