package com.sakarrobotics.cloud.common.entity;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

/**
 * Base for append-only, high-volume tables (robot_telemetry, robot_events,
 * robot_errors, command_results, task_events, audit_logs, application_logs
 * — SAKAR_ROBOT_PLATFORM_DATABASE.md). No {@code updated_at}: these rows are
 * never mutated in place, by design (Part 13's tamper-resistance rule for
 * audit_logs in particular).
 */
@Getter
@Setter
@EqualsAndHashCode(of = "id")
@MappedSuperclass
public abstract class AppendOnlyEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
