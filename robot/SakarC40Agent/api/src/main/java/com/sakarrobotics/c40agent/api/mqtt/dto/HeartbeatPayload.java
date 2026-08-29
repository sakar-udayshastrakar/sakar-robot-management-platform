package com.sakarrobotics.c40agent.api.mqtt.dto;

/** Agent health only - never robot telemetry (Phase 3 Part 7). */
public final class HeartbeatPayload {

    private final String agentVersion;
    private final long uptimeSeconds;
    private final String connectionStatus;

    public HeartbeatPayload(String agentVersion, long uptimeSeconds, String connectionStatus) {
        this.agentVersion = agentVersion;
        this.uptimeSeconds = uptimeSeconds;
        this.connectionStatus = connectionStatus;
    }

    public String getAgentVersion() {
        return agentVersion;
    }

    public long getUptimeSeconds() {
        return uptimeSeconds;
    }

    public String getConnectionStatus() {
        return connectionStatus;
    }
}
