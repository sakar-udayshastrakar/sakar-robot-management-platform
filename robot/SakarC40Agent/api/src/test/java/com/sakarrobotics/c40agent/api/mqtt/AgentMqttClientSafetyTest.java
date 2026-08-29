package com.sakarrobotics.c40agent.api.mqtt;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.Test;

/**
 * Safety/no-crash behavior that does not require a broker (Phase 3 Part
 * 17 - "Agent tests must clearly distinguish SOFTWARE TEST from PHYSICAL
 * ROBOT TEST"; everything in this class is a software test only).
 */
class AgentMqttClientSafetyTest {

    @Test
    void connect_withNoBrokerUrlConfigured_doesNotThrowAndDoesNotAttemptToConnect() {
        SakarMqttConfig unconfigured = new SakarMqttConfig(
                null, "sakar", new AgentIdentity(null, null, null, null), null, null, 60, 1, 30, 60, 30000, 200);
        AgentMqttClient client = new AgentMqttClient(unconfigured, null);

        assertDoesNotThrow(client::connect);
        assertDoesNotThrow(client::disconnect);
    }

    @Test
    void disconnect_beforeEverConnecting_doesNotThrow() {
        SakarMqttConfig config = new SakarMqttConfig(
                "tcp://localhost:1883", "sakar", new AgentIdentity("org-1", null, "robot-1", "agent-1"),
                null, null, 60, 1, 30, 60, 30000, 200);
        AgentMqttClient client = new AgentMqttClient(config, null);

        assertDoesNotThrow(client::disconnect);
    }

    @Test
    void publishingBeforeAnyConnectionAttempt_doesNotThrow_messageIsQueuedNotLost() {
        SakarMqttConfig config = new SakarMqttConfig(
                "tcp://localhost:1883", "sakar", new AgentIdentity("org-1", null, "robot-1", "agent-1"),
                null, null, 60, 1, 30, 60, 30000, 200);
        AgentMqttClient client = new AgentMqttClient(config, null);

        assertDoesNotThrow(() -> client.publishHeartbeat("0.1.0", 1, "CONNECTED"));
    }

    @Test
    void aSslBrokerUrlWithNoCustomTrustStoreConfigured_doesNotThrow_usesThePlatformDefaultTrustStore() {
        SakarMqttConfig config = new SakarMqttConfig(
                "ssl://localhost:8883", "sakar", new AgentIdentity("org-1", null, "robot-1", "agent-1"),
                null, null, 60, 1, 30, 60, 30000, 200, null, null);
        AgentMqttClient client = new AgentMqttClient(config, null);

        assertDoesNotThrow(client::connect); // connects on a background thread; failure to reach the broker is logged, not thrown here
        assertDoesNotThrow(client::disconnect);
    }

    @Test
    void aCustomTrustStorePath_doesNotThrowDuringConstruction() {
        SakarMqttConfig config = new SakarMqttConfig(
                "ssl://localhost:8883", "sakar", new AgentIdentity("org-1", null, "robot-1", "agent-1"),
                null, null, 60, 1, 30, 60, 30000, 200, "/data/local/tmp/does-not-exist.bks", "unused");
        AgentMqttClient client = new AgentMqttClient(config, null);

        assertDoesNotThrow(client::connect);
        assertDoesNotThrow(client::disconnect);
    }
}
