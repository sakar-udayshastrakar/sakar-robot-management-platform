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

    @Lob
    @Column
    private String context;
}
