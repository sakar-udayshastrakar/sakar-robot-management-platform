package com.sakarrobotics.cloud.srels;

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

/** Maps to {@code application_logs} (Master Requirements Part 12.A). Phase 1 scope: schema/entity only. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "application_logs")
public class ApplicationLog extends AppendOnlyEntity {

    @Column(nullable = false)
    private String source;

    @Column(name = "robot_id")
    private UUID robotId;

    @Column(nullable = false, length = 16)
    private String level;

    @Column(nullable = false)
    private String message;

    // Hibernate 7's default @Lob-on-String mapping expects a JDBC CLOB backed by
    // Postgres's oid large-object type; every migration in this schema instead uses
    // plain TEXT (the idiomatic Postgres choice for unbounded text) - LONGVARCHAR
    // tells Hibernate to validate/read/write this column as ordinary text, matching
    // what the migration actually created, without changing the column itself.
    @Lob
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column
    private String context;
}
