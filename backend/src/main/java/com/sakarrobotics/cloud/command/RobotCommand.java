package com.sakarrobotics.cloud.command;

import java.time.Instant;
import java.util.UUID;

import com.sakarrobotics.cloud.common.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Maps to {@code robot_commands} (Master Requirements Part 20,
 * SAKAR_ROBOT_PLATFORM_DATABASE.md §12). Carries every field the command-
 * security envelope requires: id, robot, issuer, org, timestamp,
 * expiration, nonce, and (once signing is implemented) a signature.
 *
 * <p><strong>Phase 1 scope: schema/entity only.</strong> No controller or
 * service in this codebase creates a row here yet — command creation,
 * signing, and MQTT dispatch are Phase 5 (Master Requirements roadmap).
 * Building the table now (rather than after Phase 5) lets the Part 13
 * schema be validated as a whole in Phase 1, per the implementation brief.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "robot_commands")
public class RobotCommand extends BaseEntity {

    @Column(name = "robot_id", nullable = false)
    private UUID robotId;

    @Column(name = "issued_by", nullable = false)
    private UUID issuedBy;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "command_type", nullable = false)
    private String commandType;

    // See ApplicationLog.context's comment: LONGVARCHAR matches this column's
    // actual Postgres type (TEXT, not oid) under Hibernate 7's @Lob defaults.
    @Lob
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private CommandStatus status = CommandStatus.REQUESTED;

    @Column(nullable = false)
    private String nonce;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column
    private String signature;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "completed_at")
    private Instant completedAt;
}
