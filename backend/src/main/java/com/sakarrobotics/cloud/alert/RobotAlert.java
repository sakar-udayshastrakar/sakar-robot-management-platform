package com.sakarrobotics.cloud.alert;

import java.time.Instant;
import java.util.UUID;

import com.sakarrobotics.cloud.common.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Maps to {@code robot_alerts} (Master Requirements "Alerts" section:
 * offline, low battery, critical error, task failure, emergency,
 * communication loss, lock/unlock, charging, telemetry threshold). Phase 1
 * scope: schema/entity only — alert generation requires the telemetry
 * ingestion path (Phase 2+).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "robot_alerts")
public class RobotAlert extends BaseEntity {

    @Column(name = "robot_id", nullable = false)
    private UUID robotId;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "alert_type", nullable = false)
    private String alertType;

    @Column(nullable = false, length = 16)
    private String severity;

    @Column
    private String message;

    @Column(nullable = false, length = 16)
    private String status = "OPEN";

    @Column(name = "acknowledged_by")
    private UUID acknowledgedBy;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;
}
