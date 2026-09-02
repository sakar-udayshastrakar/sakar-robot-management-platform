package com.sakarrobotics.cloud.integration.keenon;

import com.sakarrobotics.cloud.common.entity.AppendOnlyEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Raw vendor callback storage (Master Requirements "Webhooks" section):
 * every Keenon callback (RobotOnlineStatus, RobotPowerInfo, RobotWorkState,
 * RobotPositionType, CleanRobotStatus, CleanRobotRechargeTask,
 * CleanRobotFinishTask, CleanRobotPauseTask, CleanStrategyTemporary,
 * schedule callbacks, ...) is persisted here verbatim before any
 * normalization is attempted, keyed on a dedup key for idempotent
 * processing (retried deliveries must not create duplicate Sakar events).
 *
 * <p>Normalizing these into typed {@code robot_events} rows is deferred
 * past Phase 1 — see the Final Report "known limitations". This table
 * alone already satisfies "persist raw vendor event safely" and
 * "idempotent callback processing".
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "vendor_webhook_events", uniqueConstraints = @UniqueConstraint(columnNames = "dedup_key"))
public class VendorWebhookEvent extends AppendOnlyEntity {

    @Column(nullable = false, length = 32)
    private String vendor = "KEENON";

    @Column(name = "event_type")
    private String eventType;

    @Column(name = "dedup_key", nullable = false, unique = true)
    private String dedupKey;

    // See ApplicationLog.context's comment: LONGVARCHAR matches this column's
    // actual Postgres type (TEXT, not oid) under Hibernate 7's @Lob defaults.
    @Lob
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "raw_payload", nullable = false)
    private String rawPayload;

    @Column(nullable = false)
    private boolean processed = false;
}
