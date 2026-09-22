package com.sakarrobotics.cloud.telemetry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * The single authoritative online/offline rule ("Fix robot online/offline
 * status consistency" slice). These cases pin the exact behavior that was
 * previously wrong: connectivity is decided from the robot's own
 * heartbeat/telemetry timestamp against the one configured offline
 * threshold — never from whether a Keenon API call succeeded.
 */
@ExtendWith(MockitoExtension.class)
class RobotConnectivityServiceTest {

    private static final long THRESHOLD_SECONDS = 300L;

    @Mock
    private RobotStatusRepository robotStatusRepository;

    private RobotConnectivityService service() {
        return service(THRESHOLD_SECONDS);
    }

    private RobotConnectivityService service(long thresholdSeconds) {
        RobotConnectivityService service = new RobotConnectivityService(robotStatusRepository);
        setThreshold(service, thresholdSeconds);
        return service;
    }

    private static void setThreshold(RobotConnectivityService service, long thresholdSeconds) {
        try {
            var field = RobotConnectivityService.class.getDeclaredField("offlineThresholdSeconds");
            field.setAccessible(true);
            field.setLong(service, thresholdSeconds);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static RobotStatus statusSeenSecondsAgo(UUID robotId, long secondsAgo, boolean onlineFlag) {
        RobotStatus status = new RobotStatus();
        status.setRobotId(robotId);
        status.setOnline(onlineFlag);
        status.setLastSeenAt(Instant.now().minusSeconds(secondsAgo));
        return status;
    }

    // ------------------------------------------------------------------
    // The core threshold rule.
    // ------------------------------------------------------------------

    @Test
    void freshHeartbeat_isOnline() {
        RobotStatus status = statusSeenSecondsAgo(UUID.randomUUID(), 5, true);

        assertThat(service().evaluate(status)).isEqualTo(RobotConnectionStatus.ONLINE);
    }

    @Test
    void heartbeatExactlyAtTheConfiguredThreshold_isStillOnline() {
        // The boundary is inclusive, so a robot sitting exactly on the threshold does
        // not flap between ONLINE and OFFLINE on consecutive reads.
        RobotStatus status = new RobotStatus();
        status.setRobotId(UUID.randomUUID());
        status.setOnline(true);
        RobotConnectivityService service = service();
        status.setLastSeenAt(service.staleCutoff());

        assertThat(service.evaluate(status)).isEqualTo(RobotConnectionStatus.ONLINE);
    }

    @Test
    void heartbeatOlderThanTheConfiguredThreshold_isOffline_evenWhileTheStoredFlagStillSaysOnline() {
        // This is exactly the reported bug: the stored flag is only flipped by
        // RobotOfflineWatcherService's periodic sweep, so between sweeps it can still
        // read true. The derived status must not wait for the sweep.
        RobotStatus status = statusSeenSecondsAgo(UUID.randomUUID(), THRESHOLD_SECONDS + 1, true);

        assertThat(service().evaluate(status)).isEqualTo(RobotConnectionStatus.OFFLINE);
    }

    @Test
    void freshTimestampButExplicitOfflineSignal_staysOffline() {
        // MQTT Last Will, or Keenon reporting no data for this robot — an explicit
        // offline signal is more precise than freshness alone, and is preserved.
        RobotStatus status = statusSeenSecondsAgo(UUID.randomUUID(), 5, false);

        assertThat(service().evaluate(status)).isEqualTo(RobotConnectionStatus.OFFLINE);
    }

    @Test
    void neverSeen_isUnknown_neverOnline() {
        RobotStatus neverReported = new RobotStatus();
        neverReported.setRobotId(UUID.randomUUID());

        assertThat(service().evaluate(neverReported)).isEqualTo(RobotConnectionStatus.UNKNOWN);
        // A robot with no robot_status row at all is the same case.
        assertThat(service().evaluate(null)).isEqualTo(RobotConnectionStatus.UNKNOWN);
    }

    // ------------------------------------------------------------------
    // Threshold comes from configuration, not a constant.
    // ------------------------------------------------------------------

    @Test
    void thresholdIsConfigurable_notHardcoded() {
        RobotStatus status = statusSeenSecondsAgo(UUID.randomUUID(), 600, true);

        // Same row, same instant, two different configured thresholds — the verdict
        // follows the configuration, proving no value is baked into the rule.
        assertThat(service(300).evaluate(status)).isEqualTo(RobotConnectionStatus.OFFLINE);
        assertThat(service(3600).evaluate(status)).isEqualTo(RobotConnectionStatus.ONLINE);
        assertThat(service(900).offlineThreshold().toSeconds()).isEqualTo(900L);
    }

    // ------------------------------------------------------------------
    // Per-robot independence and batch lookup.
    // ------------------------------------------------------------------

    @Test
    void differentRobotsAreEvaluatedIndependently() {
        UUID fresh = UUID.randomUUID();
        UUID stale = UUID.randomUUID();
        UUID neverSeen = UUID.randomUUID();
        when(robotStatusRepository.findAllById(List.of(fresh, stale, neverSeen)))
                .thenReturn(List.of(statusSeenSecondsAgo(fresh, 10, true),
                        statusSeenSecondsAgo(stale, THRESHOLD_SECONDS + 60, true)));

        RobotConnectivityService service = service();
        Map<UUID, RobotStatus> rows = service.rowsFor(List.of(fresh, stale, neverSeen));

        assertThat(service.evaluate(rows.get(fresh))).isEqualTo(RobotConnectionStatus.ONLINE);
        assertThat(service.evaluate(rows.get(stale))).isEqualTo(RobotConnectionStatus.OFFLINE);
        // Absent from the batch result entirely — never invented as a row.
        assertThat(rows).doesNotContainKey(neverSeen);
        assertThat(service.evaluate(rows.get(neverSeen))).isEqualTo(RobotConnectionStatus.UNKNOWN);
    }

    @Test
    void rowsFor_emptyOrNullInput_queriesNothing() {
        assertThat(service().rowsFor(List.of())).isEmpty();
        assertThat(service().rowsFor(null)).isEmpty();
    }
}
