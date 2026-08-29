package com.sakarrobotics.c40agent.api.mqtt;

/**
 * Mirrors the Sakar Cloud backend's {@code MqttMessageType} enum
 * (Phase 3) - the agent only ever authors these five; {@code ACK} is
 * cloud-authored and only ever parsed here, never published.
 */
public enum MqttMessageType {
    PRESENCE,
    HEARTBEAT,
    TELEMETRY,
    EVENT,
    ERROR
}
