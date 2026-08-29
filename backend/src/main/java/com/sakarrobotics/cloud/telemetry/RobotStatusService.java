package com.sakarrobotics.cloud.telemetry;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

/**
 * Upserts the single "latest known state" row per robot (Phase 3 —
 * previously schema/entity only, per {@link RobotStatus}'s Javadoc). Both
 * {@code HeartbeatService} (online/last-seen) and {@code
 * TelemetryIngestionService} (battery/charging/state metrics) write
 * through here rather than touching {@link RobotStatusRepository} directly,
 * so "find-or-create" isn't duplicated in two places.
 */
@Service
@RequiredArgsConstructor
public class RobotStatusService {

    private final RobotStatusRepository robotStatusRepository;

    @Transactional
    public RobotStatus markOnline(UUID robotId, Instant seenAt) {
        RobotStatus status = findOrCreate(robotId);
        status.setOnline(true);
        status.setLastSeenAt(seenAt);
        status.setUpdatedAt(Instant.now());
        return robotStatusRepository.save(status);
    }

    /** Driven by the MQTT broker's Last Will and Testament on an unclean disconnect (Phase 3 Part 12). */
    @Transactional
    public RobotStatus markOffline(UUID robotId, Instant seenAt) {
        RobotStatus status = findOrCreate(robotId);
        status.setOnline(false);
        status.setLastSeenAt(seenAt);
        status.setUpdatedAt(Instant.now());
        return robotStatusRepository.save(status);
    }

    @Transactional
    public RobotStatus applyKnownMetric(UUID robotId, String metric, Double valueNumeric, String valueText, Instant recordedAt) {
        RobotStatus status = findOrCreate(robotId);
        boolean recognized = true;
        switch (metric) {
            case "battery_percent" -> status.setBatteryPercent(valueNumeric != null ? valueNumeric.intValue() : null);
            case "charging_state" -> status.setChargingState(valueText);
            case "main_state" -> status.setMainState(valueText);
            case "sub_state" -> status.setSubState(valueText);
            default -> recognized = false;
        }
        if (!recognized) {
            // Unrecognized metrics are stored in robot_telemetry only — never fabricated onto robot_status.
            return status;
        }
        status.setOnline(true);
        status.setLastSeenAt(recordedAt);
        status.setUpdatedAt(Instant.now());
        return robotStatusRepository.save(status);
    }

    /** Read-only snapshot for realtime/UI purposes — never persists a not-yet-existing row. */
    public RobotStatus current(UUID robotId) {
        return findOrCreate(robotId);
    }

    private RobotStatus findOrCreate(UUID robotId) {
        return robotStatusRepository.findById(robotId).orElseGet(() -> {
            RobotStatus status = new RobotStatus();
            status.setRobotId(robotId);
            return status;
        });
    }
}
