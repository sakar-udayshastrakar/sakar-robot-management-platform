package com.sakarrobotics.cloud.mqtt.dto;

import java.util.List;

import com.sakarrobotics.cloud.robot.adapter.dto.TelemetryReading;

/**
 * {@code messageType: TELEMETRY} payload. Reuses the existing {@link
 * TelemetryReading} shape ({@code metric}/{@code valueText}/{@code
 * valueNumeric}/{@code recordedAt}) rather than inventing a parallel one —
 * it already matches {@code robot_telemetry}'s columns exactly. Batched,
 * per {@code SAKAR_ROBOT_PLATFORM_API_SPEC.md} §2.3, so one publish can
 * carry many readings buffered while offline.
 */
public record TelemetryPayload(List<TelemetryReading> readings) {
}
