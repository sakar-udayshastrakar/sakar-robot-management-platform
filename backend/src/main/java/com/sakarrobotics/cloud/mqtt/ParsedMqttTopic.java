package com.sakarrobotics.cloud.mqtt;

import java.util.UUID;

/**
 * The result of parsing an inbound topic string (Phase 3). {@code siteId}
 * is {@code null} when the topic carries the {@code _} "no site" segment
 * (a {@link com.sakarrobotics.cloud.robot.registry.Robot} may have no
 * {@code site_id} — see {@code Robot#getSiteId()}).
 *
 * <p>These values are claims read off the wire, not yet trust — see
 * {@link MqttInboundMessageService}, which re-derives tenant ownership
 * from the registered {@code Robot} row rather than acting on these
 * directly (Master Requirements Part 15/23: "do not trust robot-provided
 * organization/site IDs").
 */
public record ParsedMqttTopic(UUID organizationId, UUID siteId, UUID robotId, MqttTopicKind kind) {
}
