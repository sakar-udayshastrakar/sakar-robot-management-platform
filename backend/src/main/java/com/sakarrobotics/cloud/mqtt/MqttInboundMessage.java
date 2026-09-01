package com.sakarrobotics.cloud.mqtt;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Maps to {@code mqtt_inbound_messages} — the idempotency ledger for
 * inbound Agent-&gt;Cloud MQTT envelopes (Phase 3 Part 9). {@code
 * (robotId, messageId)} is unique-constrained at the database level, the
 * same pattern {@code VendorWebhookEvent} uses for Keenon webhook
 * idempotency: a retried/redelivered message is a no-op, not a duplicate
 * telemetry/event/error row.
 *
 * <p>Deliberately does NOT extend {@code AppendOnlyEntity}: that base
 * class mandates a {@code created_at} column, but {@code
 * V11__mqtt_inbound_messages.sql} named this table's timestamp {@code
 * received_at} instead (a more precise name for "when the cloud received
 * this envelope") — declaring the field directly here, matching the
 * migration exactly, avoids either renaming an applied migration's column
 * or adding a redundant second timestamp.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "mqtt_inbound_messages", uniqueConstraints = @UniqueConstraint(columnNames = { "robot_id", "message_id" }))
public class MqttInboundMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "robot_id", nullable = false)
    private UUID robotId;

    @Column(name = "message_id", nullable = false)
    private String messageId;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false, length = 16)
    private MqttMessageType messageType;

    @Column
    private Long sequence;

    @CreationTimestamp
    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;
}
