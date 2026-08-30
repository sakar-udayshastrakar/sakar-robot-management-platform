package com.sakarrobotics.c40agent.api.mqtt;

/**
 * Builds this agent's own topics - the agent side of the exact scheme
 * {@code MqttTopicResolver} implements on the backend
 * ({@code {prefix}/{organizationId}/{siteId|_}/{robotId}/{kind}}). The
 * agent only ever needs to build (never parse) topics: its own for
 * publishing, and its own ack topic for subscribing.
 */
public final class AgentMqttTopics {

    private static final String NO_SITE_SEGMENT = "_";

    private final String prefix;
    private final AgentIdentity identity;

    public AgentMqttTopics(String prefix, AgentIdentity identity) {
        this.prefix = prefix;
        this.identity = identity;
    }

    public String presence() {
        return topic("presence");
    }

    public String heartbeat() {
        return topic("heartbeat");
    }

    public String telemetry() {
        return topic("telemetry");
    }

    public String events() {
        return topic("events");
    }

    public String errors() {
        return topic("errors");
    }

    public String ack() {
        return topic("ack");
    }

    /** Cloud-to-agent inbound (Roadmap Phase 6/7 "Robot Agent Command Loop"). */
    public String command() {
        return topic("commands");
    }

    private String topic(String kindSegment) {
        String siteSegment = identity.getSiteId() == null || identity.getSiteId().trim().isEmpty()
                ? NO_SITE_SEGMENT : identity.getSiteId();
        return prefix + "/" + identity.getOrganizationId() + "/" + siteSegment + "/" + identity.getRobotId() + "/" + kindSegment;
    }
}
