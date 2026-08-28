package com.sakarrobotics.cloud.srels;

import java.util.UUID;

import com.sakarrobotics.cloud.common.entity.AppendOnlyEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Maps to {@code robot_events} (Master Requirements Part 12.B,
 * SAKAR_ROBOT_PLATFORM_DATABASE.md §11). Phase 1 scope: schema/entity
 * only — nothing in this codebase writes a row here yet; that requires the
 * telemetry/agent ingestion path (Phase 2+) or normalized Keenon webhook
 * events (deferred past Phase 1, see {@code integration.keenon}).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "robot_events")
public class RobotEvent extends AppendOnlyEntity {

    @Column(name = "robot_id", nullable = false)
    private UUID robotId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(nullable = false, length = 16)
    private String severity;

    @Lob
    @Column
    private String payload;

    @Column(name = "occurred_at", nullable = false)
    private java.time.Instant occurredAt;
}
