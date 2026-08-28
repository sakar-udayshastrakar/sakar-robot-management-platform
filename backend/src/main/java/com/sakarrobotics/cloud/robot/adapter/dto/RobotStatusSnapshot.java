package com.sakarrobotics.cloud.robot.adapter.dto;

import java.time.Instant;

/**
 * A read of a robot's current state through an adapter. {@code raw} carries
 * the untranslated vendor payload for audit/debugging — never returned to a
 * Web/Mobile client directly (SAKAR_ROBOT_PLATFORM_API_SPEC.md "Vendor
 * integration boundary").
 */
public record RobotStatusSnapshot(
        String mainState,
        String subState,
        boolean online,
        Instant observedAt,
        Object raw) {
}
