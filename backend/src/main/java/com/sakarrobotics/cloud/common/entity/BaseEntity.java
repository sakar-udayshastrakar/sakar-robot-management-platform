package com.sakarrobotics.cloud.common.entity;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.data.domain.Persistable;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

/**
 * Base for every mutable, UUID-keyed entity in the schema (Master
 * Requirements Part 13 / SAKAR_ROBOT_PLATFORM_DATABASE.md conventions):
 * surrogate UUID primary key plus created_at/updated_at.
 *
 * Append-only, high-volume tables (robot_telemetry, robot_events,
 * command_results, task_events, audit_logs, application_logs) intentionally
 * do NOT extend this — see {@link AppendOnlyEntity}.
 */
@Getter
@Setter
@EqualsAndHashCode(of = "id")
@MappedSuperclass
public abstract class BaseEntity implements Persistable<UUID> {

    @Id
    @GeneratedValue
    @Column(nullable = false, updatable = false)
    private UUID id;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Override
    public boolean isNew() {
        return id == null;
    }
}
