package com.sakarrobotics.cloud.telemetry;

import java.util.UUID;

import com.sakarrobotics.cloud.common.entity.AppendOnlyEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Maps to {@code robot_telemetry} — raw time-series values
 * (SAKAR_ROBOT_PLATFORM_DATABASE.md §14). Phase 1 scope: schema/entity
 * only; the MQTT ingestion path that populates this table is Phase 2+.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "robot_telemetry")
public class RobotTelemetry extends AppendOnlyEntity {

    @Column(name = "robot_id", nullable = false)
    private UUID robotId;

    @Column(nullable = false)
    private String metric;

    @Column(name = "value_numeric")
    private Double valueNumeric;

    @Column(name = "value_text")
    private String valueText;

    @Column(name = "recorded_at", nullable = false)
    private java.time.Instant recordedAt;
}
