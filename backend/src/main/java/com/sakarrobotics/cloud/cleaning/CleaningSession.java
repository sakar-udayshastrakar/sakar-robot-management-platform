package com.sakarrobotics.cloud.cleaning;

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
 * Maps to {@code cleaning_sessions} (Master Requirements "Cleaning"
 * section). {@code vendorReference} carries the raw vendor receipt (e.g. a
 * Keenon {@code 610000}/{@code bizType}) for traceability — it is never the
 * primary key and never exposed to a client (vendor acknowledgement is not
 * physical success; see Master Requirements Part 40's governing rule).
 * Phase 1 scope: schema/entity only — populated once task dispatch and
 * reconciliation (Phase 5) exist.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "cleaning_sessions")
public class CleaningSession extends BaseEntity {

    @Column(name = "robot_id", nullable = false)
    private UUID robotId;

    @Column(name = "site_id")
    private UUID siteId;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "task_id")
    private UUID taskId;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "duration_seconds")
    private Long durationSeconds;

    @Column(name = "area_sq_meters")
    private Double areaSqMeters;

    @Column
    private Double efficiency;

    @Column
    private String result;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "vendor_reference")
    private String vendorReference;

    @Column(name = "snapshot_url")
    private String snapshotUrl;
}
