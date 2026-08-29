package com.sakarrobotics.c40agent.api.mqtt.dto;

import java.util.List;

/** Batched, per {@code SAKAR_ROBOT_PLATFORM_API_SPEC.md} §2.3. */
public final class TelemetryPayload {

    private final List<TelemetryFieldReading> readings;

    public TelemetryPayload(List<TelemetryFieldReading> readings) {
        this.readings = readings;
    }

    public List<TelemetryFieldReading> getReadings() {
        return readings;
    }
}
