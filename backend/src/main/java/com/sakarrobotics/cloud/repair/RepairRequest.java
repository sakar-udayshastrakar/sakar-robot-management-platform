package com.sakarrobotics.cloud.repair;

import java.time.Instant;
import java.util.UUID;

import com.sakarrobotics.cloud.common.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Maps to {@code repair_requests} — Operation And Maintenance Platform →
 * Customer Repair Requests. {@code siteId}/{@code robotId} are the existing
 * Site/Robot entities, never duplicated; "Associated Reseller" (the
 * reference product's term) is resolved from {@link #organizationId} by the
 * caller, same convention as Site's "affiliated agent".
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "repair_requests")
public class RepairRequest extends BaseEntity {

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "site_id")
    private UUID siteId;

    @Column(name = "robot_id")
    private UUID robotId;

    @Column(name = "work_order_number", nullable = false, unique = true)
    private String workOrderNumber;

    @Column(nullable = false)
    private String symptom;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RepairRequestStatus status = RepairRequestStatus.OPEN;

    @Column(name = "reported_by")
    private String reportedBy;

    @Column(name = "reported_at", nullable = false)
    private Instant reportedAt = Instant.now();

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column
    private String notes;
}
