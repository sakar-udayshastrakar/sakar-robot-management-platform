package com.sakarrobotics.cloud.mqtt.dto;

/**
 * {@code messageType: HEARTBEAT} payload — agent health only, never robot
 * telemetry (Phase 3 Part 7: "Heartbeat is agent health, not robot
 * health").
 */
public record HeartbeatPayload(String agentVersion, long uptimeSeconds, String connectionStatus) {
}
