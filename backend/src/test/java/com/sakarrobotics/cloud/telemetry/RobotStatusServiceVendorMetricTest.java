package com.sakarrobotics.cloud.telemetry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sakarrobotics.cloud.alert.AlertGenerationService;

/**
 * Pins the distinction that the online/offline consistency fix rests on:
 * a metric that arrived from the <em>vendor cloud</em> must never be
 * recorded as evidence that the <em>robot</em> is alive.
 *
 * <p>Before this fix, {@code KeenonStatusSyncService} wrote vendor-polled
 * state through {@code applyKnownMetric}, stamping {@code online = true}
 * and {@code last_seen_at = <the moment Sakar polled Keenon>} on every
 * sync cycle. That reset the offline clock indefinitely, so the Robots
 * page rendered ONLINE for a robot whose "No heartbeat or telemetry
 * received within the configured offline threshold" alert was open.
 */
@ExtendWith(MockitoExtension.class)
class RobotStatusServiceVendorMetricTest {

    @Mock
    private RobotStatusRepository robotStatusRepository;
    @Mock
    private AlertGenerationService alertGenerationService;

    private RobotStatusService service() {
        return new RobotStatusService(robotStatusRepository, alertGenerationService);
    }

    private void repositoryReturns(RobotStatus status) {
        when(robotStatusRepository.findById(status.getRobotId())).thenReturn(Optional.of(status));
        when(robotStatusRepository.save(any(RobotStatus.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private static RobotStatus staleRow(UUID robotId, Instant staleSince) {
        RobotStatus status = new RobotStatus();
        status.setRobotId(robotId);
        status.setOnline(false);
        status.setLastSeenAt(staleSince);
        return status;
    }

    @Test
    void applyVendorMetric_storesTheValue_butNeverTouchesLivenessFields() {
        UUID robotId = UUID.randomUUID();
        Instant staleSince = Instant.now().minusSeconds(7200);
        RobotStatus row = staleRow(robotId, staleSince);
        repositoryReturns(row);

        RobotStatus saved = service().applyVendorMetric(robotId, "main_state", null, "IDLE");

        // The vendor-reported value is genuinely useful and is stored...
        assertThat(saved.getMainState()).isEqualTo("IDLE");
        // ...but the robot has still not spoken, so neither liveness field moves.
        assertThat(saved.isOnline()).isFalse();
        assertThat(saved.getLastSeenAt()).isEqualTo(staleSince);
    }

    @Test
    void applyVendorMetric_battery_doesNotRefreshTheOfflineClock() {
        UUID robotId = UUID.randomUUID();
        Instant staleSince = Instant.now().minusSeconds(3600);
        RobotStatus row = staleRow(robotId, staleSince);
        repositoryReturns(row);

        RobotStatus saved = service().applyVendorMetric(robotId, "battery_percent", 77.0, null);

        assertThat(saved.getBatteryPercent()).isEqualTo(77);
        assertThat(saved.getLastSeenAt()).isEqualTo(staleSince);
        assertThat(saved.isOnline()).isFalse();
    }

    @Test
    void applyKnownMetric_theRealRobotPath_stillDoesUpdateLiveness() {
        // The MQTT heartbeat/telemetry path is deliberately unchanged: there the robot
        // genuinely spoke, so marking it online and advancing last_seen_at is correct.
        UUID robotId = UUID.randomUUID();
        RobotStatus row = staleRow(robotId, Instant.now().minusSeconds(7200));
        repositoryReturns(row);
        Instant reportedAt = Instant.now();

        RobotStatus saved = service().applyKnownMetric(robotId, "main_state", null, "IDLE", reportedAt);

        assertThat(saved.isOnline()).isTrue();
        assertThat(saved.getLastSeenAt()).isEqualTo(reportedAt);
    }

    @Test
    void applyVendorMetric_unrecognizedMetric_writesNothingAtAll() {
        UUID robotId = UUID.randomUUID();
        Instant staleSince = Instant.now().minusSeconds(60);
        RobotStatus row = staleRow(robotId, staleSince);
        when(robotStatusRepository.findById(robotId)).thenReturn(Optional.of(row));

        RobotStatus result = service().applyVendorMetric(robotId, "some_unmodelled_metric", 1.0, null);

        assertThat(result.getLastSeenAt()).isEqualTo(staleSince);
        verifyNoInteractions(alertGenerationService);
    }
}
