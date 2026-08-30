package com.sakarrobotics.c40agent.api.mqtt;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class AgentMqttTopicsTest {

    @Test
    void buildsTopicsMatchingTheBackendScheme_withASite() {
        AgentIdentity identity = new AgentIdentity("org-1", "site-1", "robot-1", "agent-1");
        AgentMqttTopics topics = new AgentMqttTopics("sakar", identity);

        assertEquals("sakar/org-1/site-1/robot-1/presence", topics.presence());
        assertEquals("sakar/org-1/site-1/robot-1/heartbeat", topics.heartbeat());
        assertEquals("sakar/org-1/site-1/robot-1/telemetry", topics.telemetry());
        assertEquals("sakar/org-1/site-1/robot-1/events", topics.events());
        assertEquals("sakar/org-1/site-1/robot-1/errors", topics.errors());
        assertEquals("sakar/org-1/site-1/robot-1/ack", topics.ack());
        assertEquals("sakar/org-1/site-1/robot-1/commands", topics.command());
    }

    @Test
    void aRobotWithNoSite_usesTheNoSiteSegment() {
        AgentIdentity identity = new AgentIdentity("org-1", null, "robot-1", "agent-1");
        AgentMqttTopics topics = new AgentMqttTopics("sakar", identity);

        assertEquals("sakar/org-1/_/robot-1/heartbeat", topics.heartbeat());
    }

    @Test
    void aRobotWithABlankSite_usesTheNoSiteSegment() {
        AgentIdentity identity = new AgentIdentity("org-1", "  ", "robot-1", "agent-1");
        AgentMqttTopics topics = new AgentMqttTopics("sakar", identity);

        assertEquals("sakar/org-1/_/robot-1/telemetry", topics.telemetry());
    }
}
