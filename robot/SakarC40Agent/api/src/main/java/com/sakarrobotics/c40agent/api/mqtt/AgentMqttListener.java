package com.sakarrobotics.c40agent.api.mqtt;

import com.sakarrobotics.c40agent.api.mqtt.dto.AckPayload;

/** Lifecycle/ack callbacks (Phase 3 Part 12/13) - every method has a no-op default. */
public interface AgentMqttListener {

    default void onConnected(boolean reconnect) {
    }

    default void onConnectionLost(Throwable cause) {
    }

    default void onReconnecting() {
    }

    default void onAckReceived(AckPayload ack) {
    }
}
