package com.sakarrobotics.cloud.lock;

import java.util.UUID;

import com.sakarrobotics.cloud.common.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Maps to {@code robot_locks} (Master Requirements Part 11 — "the single
 * most safety-critical requirement in the entire platform"). Phase 1 scope:
 * schema/entity only. No controller in this codebase creates a row here —
 * remote lock/unlock software infrastructure (permission gating, signing,
 * step-up auth) belongs to the command module (Phase 5) and the physical
 * effect remains {@code REQUIRES PHYSICAL C40 TEST} regardless
 * (Master Requirements Part 38). {@code physicallyConfirmed} defaults to
 * {@code false} and nothing in this codebase ever sets it {@code true}.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "robot_locks")
public class RobotLock extends BaseEntity {

    @Column(name = "robot_id", nullable = false)
    private UUID robotId;

    @Column(nullable = false, length = 16)
    private String action; // LOCK | UNLOCK

    @Column(name = "requested_by", nullable = false)
    private UUID requestedBy;

    @Column(nullable = false)
    private String reason;

    @Column(name = "command_id")
    private UUID commandId;

    @Column(nullable = false, length = 16)
    private String result = "PENDING";

    @Column(name = "physically_confirmed", nullable = false)
    private boolean physicallyConfirmed = false;
}
