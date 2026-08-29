package com.sakarrobotics.cloud.mqtt;

import java.util.UUID;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * Builds and parses the platform's MQTT topic scheme (Phase 3).
 *
 * <p><strong>Chosen scheme (organization/site/robot-namespaced, per
 * {@code SAKAR_SECURITY_REQUIREMENTS.md} §10's topic-isolation
 * requirement — this document is the "existing specification" the Phase 3
 * task instructions defer to over a fresh proposal):</strong>
 * <pre>
 * {prefix}/{organizationId}/{siteId|_}/{robotId}/presence
 * {prefix}/{organizationId}/{siteId|_}/{robotId}/heartbeat
 * {prefix}/{organizationId}/{siteId|_}/{robotId}/telemetry
 * {prefix}/{organizationId}/{siteId|_}/{robotId}/events
 * {prefix}/{organizationId}/{siteId|_}/{robotId}/errors
 * {prefix}/{organizationId}/{siteId|_}/{robotId}/ack
 * </pre>
 * {@code _} is the literal "no site assigned" segment — {@link
 * com.sakarrobotics.cloud.robot.registry.Robot#getSiteId()} is nullable.
 * No segment ever carries anything beyond these three UUIDs and a fixed
 * suffix word — no PII, no credential, no free-text (Phase 3 topic-design
 * requirement: "Topics MUST NOT contain sensitive information").
 *
 * <p>The organization/site segments exist for broker-side ACL scoping in
 * a production deployment (a robot's credential should only be grantable
 * publish/subscribe rights on its own {@code {org}/{site}/{robot}}
 * prefix) — see {@code SAKAR_MQTT_ARCHITECTURE.md} "Security" for what is
 * and is not enforced by the current dev broker. The backend itself never
 * relies on these segments for authorization; see
 * {@link MqttInboundMessageService}.
 */
@Component
@RequiredArgsConstructor
public class MqttTopicResolver {

    static final String NO_SITE_SEGMENT = "_";

    private final MqttProperties properties;

    public String topic(UUID organizationId, UUID siteId, UUID robotId, MqttTopicKind kind) {
        return properties.getTopicPrefix() + "/" + organizationId + "/" + siteSegment(siteId) + "/" + robotId + "/" + kind.segment();
    }

    /** Broker subscription filter for every robot's topic of the given kind, e.g. {@code sakar/+/+/+/telemetry}. */
    public String subscriptionFilter(MqttTopicKind kind) {
        return properties.getTopicPrefix() + "/+/+/+/" + kind.segment();
    }

    /** One filter per inbound (agent-authored) kind — everything {@link MqttInboundListener} subscribes to. */
    public String[] inboundSubscriptionFilters() {
        return new String[] {
                subscriptionFilter(MqttTopicKind.PRESENCE),
                subscriptionFilter(MqttTopicKind.HEARTBEAT),
                subscriptionFilter(MqttTopicKind.TELEMETRY),
                subscriptionFilter(MqttTopicKind.EVENTS),
                subscriptionFilter(MqttTopicKind.ERRORS),
        };
    }

    /**
     * @throws IllegalArgumentException if the topic does not match {@code
     * {prefix}/{org}/{site}/{robot}/{kind}} with two well-formed UUIDs and
     * a recognized kind segment — callers must treat this as "malformed,
     * reject" (Phase 3 requirement), never as a crash.
     */
    public ParsedMqttTopic parse(String topic) {
        String[] parts = topic.split("/");
        if (parts.length != 5 || !properties.getTopicPrefix().equals(parts[0])) {
            throw new IllegalArgumentException("Malformed Sakar MQTT topic: " + topic);
        }
        UUID organizationId = UUID.fromString(parts[1]);
        UUID siteId = NO_SITE_SEGMENT.equals(parts[2]) ? null : UUID.fromString(parts[2]);
        UUID robotId = UUID.fromString(parts[3]);
        MqttTopicKind kind = MqttTopicKind.fromSegment(parts[4]);
        return new ParsedMqttTopic(organizationId, siteId, robotId, kind);
    }

    private static String siteSegment(UUID siteId) {
        return siteId == null ? NO_SITE_SEGMENT : siteId.toString();
    }
}
