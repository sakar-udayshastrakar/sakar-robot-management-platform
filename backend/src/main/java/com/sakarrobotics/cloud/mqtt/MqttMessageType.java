package com.sakarrobotics.cloud.mqtt;

/**
 * The generic Sakar Robot Agent &lt;-&gt; Sakar Cloud MQTT envelope's
 * {@code messageType} (Phase 3, Master Requirements Part 15/23,
 * SAKAR_ROBOT_PLATFORM_API_SPEC.md §2). {@link #ACK} and {@link #COMMAND}
 * are backend-authored/cloud-to-agent; the rest are agent-authored.
 * {@link #COMMAND} is declared for topic/envelope completeness only — no
 * command dispatch is implemented in Phase 3 (remote lock/unlock and
 * arbitrary robot commands remain out of scope, see Master Requirements
 * Part 11/38).
 */
public enum MqttMessageType {
    PRESENCE,
    HEARTBEAT,
    TELEMETRY,
    EVENT,
    ERROR,
    ACK,
    COMMAND
}
