package com.sakarrobotics.c40agent;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.sakarrobotics.c40agent.api.mqtt.TelemetrySnapshotProvider;
import com.sakarrobotics.c40agent.api.mqtt.dto.TelemetryFieldReading;
import com.sakarrobotics.c40agent.logging.SdkCallLogger;
import com.sakarrobotics.c40agent.robot.C40RobotController;
import com.sakarrobotics.c40agent.telemetry.ConnectionStatus;
import com.sakarrobotics.c40agent.telemetry.RuntimeSnapshot;

/**
 * The concrete "Telemetry Provider" from Phase 3 Part 6's architecture
 * diagram - reads only {@link C40RobotController}'s existing public,
 * read-only methods (never {@code :sdk}/{@code com.keenon.*} directly, and
 * never one of the actuating methods gated by {@code OperatingMode}).
 *
 * <p>Only publishes fields {@link RuntimeSnapshot} actually exposes.
 * {@code power} is mapped to {@code battery_percent} because
 * {@code SAKAR_ROBOT_PLATFORM_REQUIREMENTS.md} documents
 * "Battery/online-state: CONFIRMED available via RuntimeInfo.getPower()"
 * - this is the platform's own documented interpretation, not an
 * invented one. Robot position is deliberately never published here: the
 * only SDK path for it ({@code queryRobotPosition}) is marked
 * <strong>UNCONFIRMED on the physical C40</strong> in this codebase's own
 * documentation (see {@code PeanutSdkBridge#queryRobotPosition}), and
 * battery/motor-status read via the raw async SDK calls
 * ({@code getBattery}/{@code getMotorStatus}) are deliberately left
 * unparsed (see {@code RawSnapshot}'s Javadoc: "the Peanut SDK v1.3.0
 * documentation does not publish a confirmed JSON schema for these
 * responses") - inventing a parser for them here would be exactly the
 * kind of fabrication Phase 3 forbids.
 */
final class C40TelemetrySnapshotProvider implements TelemetrySnapshotProvider {

    private final C40RobotController controller;

    C40TelemetrySnapshotProvider(C40RobotController controller) {
        this.controller = controller;
    }

    @Override
    public List<TelemetryFieldReading> currentReadings() {
        if (controller == null || controller.getConnectionStatus() != ConnectionStatus.CONNECTED) {
            return Collections.emptyList(); // not connected - REQUIRES PHYSICAL TEST / no data available yet
        }
        RuntimeSnapshot snapshot;
        try {
            snapshot = controller.getRuntimeInfo();
        } catch (RuntimeException notYetPopulated) {
            SdkCallLogger.getInstance().logError("C40TelemetrySnapshotProvider.currentReadings", "n/a", -1,
                    "getRuntimeInfo() not yet available: " + notYetPopulated.getMessage());
            return Collections.emptyList();
        }
        if (snapshot == null) {
            return Collections.emptyList();
        }

        String now = Instant.now().toString();
        List<TelemetryFieldReading> readings = new ArrayList<>();
        readings.add(new TelemetryFieldReading("battery_percent", null, (double) snapshot.getPower(), now));
        readings.add(new TelemetryFieldReading("work_mode", null, (double) snapshot.getWorkMode(), now));
        readings.add(new TelemetryFieldReading("sync_status", null, (double) snapshot.getSyncStatus(), now));
        readings.add(new TelemetryFieldReading("motor_status", null, (double) snapshot.getMotorStatus(), now));
        if (snapshot.getTotalOdo() != null) {
            readings.add(new TelemetryFieldReading("total_odo", null, snapshot.getTotalOdo(), now));
        }
        readings.add(new TelemetryFieldReading("emergency_enable", String.valueOf(snapshot.isEmergencyEnable()), null, now));
        readings.add(new TelemetryFieldReading("emergency_open", String.valueOf(snapshot.isEmergencyOpen()), null, now));
        if (snapshot.getRobotIp() != null) {
            readings.add(new TelemetryFieldReading("robot_ip", snapshot.getRobotIp(), null, now));
        }
        return readings;
    }
}
