package com.sakarrobotics.cloud.telemetry.dto;

import java.time.Instant;
import java.util.UUID;

import com.sakarrobotics.cloud.telemetry.RobotStatus;

/** Real-time WebSocket payload shape for a robot status change (Phase 3 Part 15). */
public record RobotStatusUpdate(
        UUID robotId,
        boolean online,
        Integer batteryPercent,
        String chargingState,
        String mainState,
        String subState,
        Instant lastSeenAt) {

    public static RobotStatusUpdate from(RobotStatus status) {
        return new RobotStatusUpdate(
                status.getRobotId(), status.isOnline(), status.getBatteryPercent(),
                status.getChargingState(), status.getMainState(), status.getSubState(), status.getLastSeenAt());
    }
}
