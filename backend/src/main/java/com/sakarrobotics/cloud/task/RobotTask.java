package com.sakarrobotics.cloud.task;

import java.util.UUID;

import com.sakarrobotics.cloud.common.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Maps to {@code robot_tasks} (Master Requirements "Task Model" —
 * vendor-neutral: {@code type}/{@code areaIds}/{@code mode}/
 * {@code repeatCount}/{@code returnToDock}, never a Keenon field name).
 * Phase 1 scope: schema/entity only — task creation/dispatch is Phase 5.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "robot_tasks")
public class RobotTask extends BaseEntity {

    @Column(name = "robot_id", nullable = false)
    private UUID robotId;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "task_type", nullable = false)
    private String taskType;

    // See ApplicationLog.context's comment: LONGVARCHAR matches this column's
    // actual Postgres type (TEXT, not oid) under Hibernate 7's @Lob defaults.
    @Lob
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column
    private String parameters;

    @Column(nullable = false, length = 24)
    private String status = "CREATED";
}
