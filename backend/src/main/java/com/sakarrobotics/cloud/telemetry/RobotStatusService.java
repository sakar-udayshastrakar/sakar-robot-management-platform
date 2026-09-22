package com.sakarrobotics.cloud.telemetry;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sakarrobotics.cloud.alert.AlertGenerationService;

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
    private final AlertGenerationService alertGenerationService;

    @Transactional
    public RobotStatus markOnline(UUID robotId, Instant seenAt) {
        RobotStatus status = findOrCreate(robotId);
        boolean wasOffline = !status.isOnline();
        status.setOnline(true);
        status.setLastSeenAt(seenAt);
        status.setUpdatedAt(Instant.now());
        RobotStatus saved = robotStatusRepository.save(status);
        if (wasOffline) {
            // Closes the loop on RobotOfflineWatcherService's own alert — a robot reporting back in
            // is real evidence the condition cleared, not a fabricated resolution.
            alertGenerationService.resolveOffline(robotId);
        }
        return saved;
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

    /**
     * Applies a metric that came from the <em>vendor cloud</em> (Keenon) rather
     * than from the robot itself, and therefore deliberately does <strong>not</strong>
     * touch {@code online}/{@code last_seen_at}.
     *
     * <p>A successful Keenon API call proves that Keenon's cloud answered — it is
     * no evidence at all that the robot is alive and talking. Treating it as a
     * heartbeat is exactly what made the Robots page render ONLINE for a robot the
     * alert layer had already flagged with "No heartbeat or telemetry received
     * within the configured offline threshold". Vendor-reported battery/state is
     * still worth storing; vendor-reported <em>liveness</em> is not something this
     * endpoint can honestly supply.
     *
     * @see #applyKnownMetric for the real-robot (MQTT heartbeat/telemetry) path,
     *      which does update liveness because the robot genuinely spoke.
     */
    @Transactional
    public RobotStatus applyVendorMetric(UUID robotId, String metric, Double valueNumeric, String valueText) {
        RobotStatus status = findOrCreate(robotId);
        if (!applyMetricValue(status, robotId, metric, valueNumeric, valueText)) {
            // Unrecognized metrics are stored in robot_telemetry only — never fabricated onto robot_status.
            return status;
        }
        status.setUpdatedAt(Instant.now());
        return robotStatusRepository.save(status);
    }

    @Transactional
    public RobotStatus applyKnownMetric(UUID robotId, String metric, Double valueNumeric, String valueText, Instant recordedAt) {
        RobotStatus status = findOrCreate(robotId);
        if (!applyMetricValue(status, robotId, metric, valueNumeric, valueText)) {
            // Unrecognized metrics are stored in robot_telemetry only — never fabricated onto robot_status.
            return status;
        }
        status.setOnline(true);
        status.setLastSeenAt(recordedAt);
        status.setUpdatedAt(Instant.now());
        return robotStatusRepository.save(status);
    }

    /** @return {@code true} if the metric was recognized and written onto {@code status}. */
    private boolean applyMetricValue(RobotStatus status, UUID robotId, String metric, Double valueNumeric, String valueText) {
        boolean recognized = true;
        switch (metric) {
            case "battery_percent" -> {
                Integer percent = valueNumeric != null ? valueNumeric.intValue() : null;
                status.setBatteryPercent(percent);
                if (percent != null) {
                    // Roadmap Phase 6/9 alert generation — real telemetry, not a fabricated reading.
                    alertGenerationService.evaluateBattery(robotId, percent);
                }
            }
            case "charging_state" -> status.setChargingState(valueText);
            case "main_state" -> status.setMainState(valueText);
            case "sub_state" -> status.setSubState(valueText);
            default -> recognized = false;
        }
        return recognized;
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
