package com.sakarrobotics.cloud.alert;

import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sakarrobotics.cloud.telemetry.RobotConnectivityService;
import com.sakarrobotics.cloud.telemetry.RobotStatus;
import com.sakarrobotics.cloud.telemetry.RobotStatusRepository;

import lombok.RequiredArgsConstructor;

/**
 * Periodic stale-heartbeat sweep (Roadmap Phase 6/9 "Alerts"). Real, not
 * simulated: it queries the actual {@code robot_status} rows written by
 * the Phase 3 MQTT heartbeat/telemetry path and flips a robot offline only
 * when its own {@code last_seen_at} has genuinely gone stale — never a
 * fabricated status.
 *
 * <p>Gated by {@code sakar.alerts.enabled} (default {@code true}, forced
 * {@code false} in the test profile — see {@code application-test.yml})
 * so unit/integration tests never race a wall-clock-driven background
 * sweep, matching the existing {@code sakar.mqtt.enabled} convention.
 */
@Service
@RequiredArgsConstructor
public class RobotOfflineWatcherService {

    private static final Logger log = LoggerFactory.getLogger(RobotOfflineWatcherService.class);

    private final RobotStatusRepository robotStatusRepository;
    private final AlertGenerationService alertGenerationService;
    private final RobotConnectivityService robotConnectivityService;

    @Value("${sakar.alerts.enabled:true}")
    private boolean enabled;

    @Scheduled(fixedDelayString = "${sakar.alerts.offline-check-interval-ms:60000}")
    @Transactional
    public void sweep() {
        if (!enabled) {
            return;
        }
        // The threshold itself is owned by RobotConnectivityService — the one place the
        // whole application evaluates "stale", so this sweep and every status the UI
        // renders can never drift apart on the cutoff.
        Instant cutoff = robotConnectivityService.staleCutoff();
        List<RobotStatus> stale = robotStatusRepository.findByOnlineTrueAndLastSeenAtBefore(cutoff);
        for (RobotStatus status : stale) {
            status.setOnline(false);
            status.setUpdatedAt(Instant.now());
            robotStatusRepository.save(status);
            alertGenerationService.evaluateOffline(status.getRobotId());
            log.info("Robot {} marked offline (no heartbeat since {})", status.getRobotId(), status.getLastSeenAt());
        }
    }
}
