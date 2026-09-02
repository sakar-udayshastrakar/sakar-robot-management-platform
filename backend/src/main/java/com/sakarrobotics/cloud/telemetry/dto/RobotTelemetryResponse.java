package com.sakarrobotics.cloud.telemetry.dto;

import java.time.Instant;
import java.util.UUID;

import com.sakarrobotics.cloud.telemetry.RobotTelemetry;

public record RobotTelemetryResponse(
        Long id,
        UUID robotId,
        String metric,
        Double valueNumeric,
        String valueText,
        Instant recordedAt,
        Instant createdAt) {

    public static RobotTelemetryResponse from(RobotTelemetry telemetry) {
        return new RobotTelemetryResponse(telemetry.getId(), telemetry.getRobotId(), telemetry.getMetric(),
                telemetry.getValueNumeric(), telemetry.getValueText(), telemetry.getRecordedAt(), telemetry.getCreatedAt());
    }
}
