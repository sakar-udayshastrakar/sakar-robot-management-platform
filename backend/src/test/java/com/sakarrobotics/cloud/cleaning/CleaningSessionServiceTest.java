package com.sakarrobotics.cloud.cleaning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sakarrobotics.cloud.robot.registry.Robot;

/**
 * Verifies {@link CleaningSessionService#recordFromKeenonHistory} — the
 * Keenon-history-sync writer — is append-only and idempotent per {@code
 * vendorReference}: a duplicate is skipped, never overwritten, and a fresh
 * one is created with tenant ownership derived from the robot, never the
 * (nonexistent) vendor-supplied organization/site.
 */
@ExtendWith(MockitoExtension.class)
class CleaningSessionServiceTest {

    @Mock
    private CleaningSessionRepository cleaningSessionRepository;

    private CleaningSessionService service() {
        return new CleaningSessionService(cleaningSessionRepository);
    }

    private Robot aRobot() {
        Robot robot = new Robot();
        robot.setId(UUID.randomUUID());
        robot.setOrganizationId(UUID.randomUUID());
        robot.setSiteId(UUID.randomUUID());
        return robot;
    }

    @Test
    void recordFromKeenonHistory_newVendorReference_createsARowWithTenantOwnershipFromTheRobot() {
        Robot robot = aRobot();
        String vendorReference = "keenon-log:area=13.24;efficiency=429.57;duration=229;mState=1;failDescCode=0";
        when(cleaningSessionRepository.existsByVendorReference(vendorReference)).thenReturn(false);
        when(cleaningSessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Optional<CleaningSession> result = service().recordFromKeenonHistory(robot, 13.24, 429.57, 229L, "mState:1",
                null, "https://example.com/snapshot.png", vendorReference);

        assertThat(result).isPresent();
        CleaningSession saved = result.get();
        assertThat(saved.getRobotId()).isEqualTo(robot.getId());
        assertThat(saved.getOrganizationId()).isEqualTo(robot.getOrganizationId());
        assertThat(saved.getSiteId()).isEqualTo(robot.getSiteId());
        assertThat(saved.getAreaSqMeters()).isEqualTo(13.24);
        assertThat(saved.getEfficiency()).isEqualTo(429.57);
        assertThat(saved.getDurationSeconds()).isEqualTo(229L);
        assertThat(saved.getResult()).isEqualTo("mState:1");
        assertThat(saved.getSnapshotUrl()).isEqualTo("https://example.com/snapshot.png");
        assertThat(saved.getVendorReference()).isEqualTo(vendorReference);
        // Never a fabricated Sakar task — this row didn't originate from a Sakar-dispatched task.
        assertThat(saved.getTaskId()).isNull();
    }

    @Test
    void recordFromKeenonHistory_existingVendorReference_isSkipped_neverOverwritten() {
        Robot robot = aRobot();
        String vendorReference = "keenon-log:area=13.24;efficiency=429.57;duration=229;mState=1;failDescCode=0";
        when(cleaningSessionRepository.existsByVendorReference(vendorReference)).thenReturn(true);

        Optional<CleaningSession> result = service().recordFromKeenonHistory(robot, 13.24, 429.57, 229L, "mState:1",
                null, null, vendorReference);

        assertThat(result).isEmpty();
        verify(cleaningSessionRepository, never()).save(any());
    }
}
