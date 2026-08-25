package com.sakarrobotics.c40agent.telemetry;

/** One RUNTIME_HEALTH / heartbeat callback received from PeanutRuntime.Listener. */
public final class HealthEvent {

    public enum Kind { EVENT, HEALTH, HEARTBEAT }

    private final long timestamp;
    private final Kind kind;
    private final String rawContent;

    public HealthEvent(Kind kind, String rawContent) {
        this.timestamp = System.currentTimeMillis();
        this.kind = kind;
        this.rawContent = rawContent;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public Kind getKind() {
        return kind;
    }

    public String getRawContent() {
        return rawContent;
    }
}
