package com.sakarrobotics.cloud.srels;

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
 * Maps to {@code robot_errors} (Master Requirements Part 12.C). Mutable
 * (has a resolution lifecycle) — extends {@code BaseEntity}, not
 * {@code AppendOnlyEntity}. Phase 1 scope: schema/entity only.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "robot_errors")
public class RobotError extends BaseEntity {

    @Column(name = "robot_id", nullable = false)
    private UUID robotId;

    @Column(name = "error_code", nullable = false)
    private String errorCode;

    @Column(nullable = false, length = 16)
    private String severity;

    @Column
    private String source;

    @Column
    private String message;

    @Column(name = "sdk_api")
    private String sdkApi;

    @Column(nullable = false, length = 16)
    private String status = "OPEN";

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt = Instant.now();

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolved_by")
    private UUID resolvedBy;
}
