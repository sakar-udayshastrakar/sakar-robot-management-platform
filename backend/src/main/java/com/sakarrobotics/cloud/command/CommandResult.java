package com.sakarrobotics.cloud.command;

import java.util.UUID;

import com.sakarrobotics.cloud.common.entity.AppendOnlyEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Maps to {@code command_results} (Master Requirements Part 20). Phase 1 scope: schema/entity only. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "command_results")
public class CommandResult extends AppendOnlyEntity {

    @Column(name = "command_id", nullable = false)
    private UUID commandId;

    @Column(nullable = false, length = 24)
    private String result;

    // See ApplicationLog.context's comment: LONGVARCHAR matches this column's
    // actual Postgres type (TEXT, not oid) under Hibernate 7's @Lob defaults.
    @Lob
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column
    private String detail;

    @Column(name = "duration_ms")
    private Long durationMs;
}
