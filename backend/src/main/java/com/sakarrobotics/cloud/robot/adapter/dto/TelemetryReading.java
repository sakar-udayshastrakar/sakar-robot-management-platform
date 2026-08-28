package com.sakarrobotics.cloud.robot.adapter.dto;

import java.time.Instant;

public record TelemetryReading(String metric, String valueText, Double valueNumeric, Instant recordedAt) {
}
