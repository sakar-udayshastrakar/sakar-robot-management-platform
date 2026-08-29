package com.sakarrobotics.cloud.mqtt;

import java.util.UUID;

import com.sakarrobotics.cloud.common.entity.AppendOnlyEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "mqtt_inbound_messages", uniqueConstraints = @UniqueConstraint(columnNames = { "robot_id", "message_id" }))
public class MqttInboundMessage extends AppendOnlyEntity {

    @Column(name = "robot_id", nullable = false)
    private UUID robotId;

    @Column(name = "message_id", nullable = false)
    private String messageId;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false, length = 16)
    private MqttMessageType messageType;

    @Column
    private Long sequence;
}
