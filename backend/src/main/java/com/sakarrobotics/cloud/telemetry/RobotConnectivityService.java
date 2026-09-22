package com.sakarrobotics.cloud.telemetry;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

/**
 * The single authoritative answer to "is this robot connected?" for the
 * whole application — the Robots list, the robot detail page, the
 * dashboard, and {@code RobotOfflineWatcherService}'s alert sweep all
 * evaluate the offline threshold through this one class, so no two
 * surfaces can disagree.
 *
 * <p><strong>Why this exists.</strong> Connectivity was previously read
 * off {@code RobotStatusSnapshot.online()} — an adapter field that means
 * "the vendor API returned a data object", i.e. a statement about Keenon's
 * cloud being reachable, not about the robot being alive. A robot silent
 * for hours therefore rendered as ONLINE while the alert layer, which
 * correctly reads {@code robot_status.last_seen_at}, had an open "No
 * heartbeat or telemetry received within the configured offline threshold"
 * alert for that same robot.
 *
 * <p><strong>Threshold.</strong> Reuses the existing, already-configured
 * {@code sakar.alerts.offline-threshold-seconds} (default 300s) — the same
 * property {@code RobotOfflineWatcherService} has always used. No second
 * threshold is introduced: that service now derives its sweep cutoff from
 * {@link #staleCutoff()} here, so the value is read and applied in exactly
 * one place.
 *
 * <p><strong>Time handling.</strong> Every comparison is server-side, on
 * UTC {@link Instant}s as stored in {@code robot_status.last_seen_at}. No
 * browser clock is involved anywhere in the decision.
 */
@Service
@RequiredArgsConstructor
public class RobotConnectivityService {

    private final RobotStatusRepository robotStatusRepository;

    @Value("${sakar.alerts.offline-threshold-seconds:300}")
    private long offlineThresholdSeconds;

    /** The configured threshold, exposed so callers never re-read the property themselves. */
    public Duration offlineThreshold() {
        return Duration.ofSeconds(offlineThresholdSeconds);
    }

    /**
     * Timestamps at or after this instant are considered fresh. A robot whose
     * {@code lastSeenAt} falls exactly on the cutoff is still ONLINE — the
     * threshold is inclusive, so "exactly at the threshold" never flaps.
     */
    public Instant staleCutoff() {
        return Instant.now().minusSeconds(offlineThresholdSeconds);
    }

    /**
     * The one status rule. A successful Keenon synchronization never appears in
     * this decision — only the robot's own last heartbeat/telemetry timestamp and
     * any explicit offline signal already recorded on the row.
     */
    public RobotConnectionStatus evaluate(RobotStatus status) {
        if (status == null || status.getLastSeenAt() == null) {
            return RobotConnectionStatus.UNKNOWN;
        }
        if (status.getLastSeenAt().isBefore(staleCutoff())) {
            return RobotConnectionStatus.OFFLINE;
        }
        // Fresh timestamp, but an explicit offline signal (MQTT Last Will, or Keenon
        // reporting no data for this robot) still wins — this preserves the existing,
        // more precise behavior rather than reducing everything to a freshness check.
        return status.isOnline() ? RobotConnectionStatus.ONLINE : RobotConnectionStatus.OFFLINE;
    }

    public RobotConnectionStatus statusFor(UUID robotId) {
        return evaluate(robotStatusRepository.findById(robotId).orElse(null));
    }

    /** The row itself, for callers that also need {@code lastSeenAt} for display. */
    public RobotStatus rowFor(UUID robotId) {
        return robotStatusRepository.findById(robotId).orElse(null);
    }

    /**
     * Batch lookup for the robots list — one query for the whole page rather than
     * one per row. Robots with no {@code robot_status} row at all are simply absent
     * from the returned map, and callers treat that as {@link
     * RobotConnectionStatus#UNKNOWN}.
     */
    public Map<UUID, RobotStatus> rowsFor(Collection<UUID> robotIds) {
        Map<UUID, RobotStatus> byRobotId = new HashMap<>();
        if (robotIds == null || robotIds.isEmpty()) {
            return byRobotId;
        }
        for (RobotStatus status : robotStatusRepository.findAllById(robotIds)) {
            byRobotId.put(status.getRobotId(), status);
        }
        return byRobotId;
    }
}
