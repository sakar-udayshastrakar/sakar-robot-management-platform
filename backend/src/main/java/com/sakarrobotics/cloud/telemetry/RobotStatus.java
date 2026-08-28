package com.sakarrobotics.cloud.telemetry;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Maps to {@code robot_status} — latest-known-state, upserted per robot
 * (SAKAR_ROBOT_PLATFORM_DATABASE.md §9). Phase 1 scope: schema/entity only.
 * Populating this table requires the MQTT telemetry ingestion path
 * (Master Requirements roadmap Phase 2) — not implemented in Phase 1; see
 * the Final Report "known limitations".
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "robot_status")
public class RobotStatus {

    @Id
    @Column(name = "robot_id")
    private UUID robotId;

    @Column
    private boolean online;

    @Column(name = "battery_percent")
    private Integer batteryPercent;

    @Column(name = "charging_state")
    private String chargingState;

    @Column(name = "main_state")
    private String mainState;

    @Column(name = "sub_state")
    private String subState;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
