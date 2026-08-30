package com.sakarrobotics.c40agent.api.mqtt;

import com.sakarrobotics.c40agent.api.mqtt.dto.AckPayload;
import com.sakarrobotics.c40agent.api.mqtt.dto.CommandPayload;

/** Lifecycle/ack/command callbacks (Phase 3 Part 12/13, Phase 6/7 commands) - every method has a no-op default. */
public interface AgentMqttListener {

    default void onConnected(boolean reconnect) {
    }

    default void onConnectionLost(Throwable cause) {
    }

    default void onReconnecting() {
    }

    default void onAckReceived(AckPayload ack) {
    }

    /** Roadmap Phase 6/7 "Robot Agent Command Loop" - see {@link CommandDispatcher}. */
    default void onCommandReceived(CommandPayload command) {
    }
}
