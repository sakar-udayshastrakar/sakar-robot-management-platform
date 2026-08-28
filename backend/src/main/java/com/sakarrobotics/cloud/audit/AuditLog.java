package com.sakarrobotics.cloud.audit;

import java.util.UUID;

import com.sakarrobotics.cloud.common.entity.AppendOnlyEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Maps to {@code audit_logs} (SAKAR_ROBOT_PLATFORM_DATABASE.md §17 /
 * Master Requirements Part 12.E). Append-only by construction: no service
 * or repository method in this codebase updates or deletes a row here
 * (Part 13's "database-level tamper-resistance" requirement — the actual
 * DB-role-level {@code REVOKE UPDATE, DELETE} is a deployment/DBA step
 * outside this application's own migrations, noted as a known limitation).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "audit_logs")
public class AuditLog extends AppendOnlyEntity {

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "organization_id")
    private UUID organizationId;

    @Column(name = "robot_id")
    private UUID robotId;

    @Column(nullable = false)
    private String action;

    @Column(nullable = false)
    private String result;

    @Column
    private String reason;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column
    private String device;

    @Column(name = "request_id")
    private String requestId;
}
