package com.sakarrobotics.cloud.robot.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sakarrobotics.cloud.robot.registry.dto.RobotSerialReconciliationResult;

/**
 * Unit tests for {@link RobotSerialReconciliationService} against mocked repositories — proves
 * the legacy-detection rule, deterministic ordering, and idempotency logic. {@link
 * SakarSerialNumberService} is mocked here since its own uniqueness/concurrency guarantees are
 * already proven against a real database in {@code SakarSerialNumberServiceTest}; real,
 * full-database round-tripping (including genuine transactional rollback) is covered separately
 * by {@code RobotSerialReconciliationControllerTest}. Each legacy robot's model is stubbed with a
 * generic {@code "CB"}-prefixed model — the exact resolved-model wiring is proven separately in
 * {@code KeenonRobotSyncServiceTest}.
 */
@ExtendWith(MockitoExtension.class)
class RobotSerialReconciliationServiceTest {

    private static final UUID ORG_ID = UUID.randomUUID();

    @Mock
    private RobotRepository robotRepository;
    @Mock
    private RobotModelRepository robotModelRepository;
    @Mock
    private SakarSerialNumberService sakarSerialNumberService;

    private RobotSerialReconciliationService service() {
        return new RobotSerialReconciliationService(robotRepository, robotModelRepository, sakarSerialNumberService);
    }

    private static Robot legacyRobot(String externalRobotId, String legacyVendorSerial) {
        Robot robot = new Robot();
        robot.setId(UUID.randomUUID());
        robot.setOrganizationId(ORG_ID);
        robot.setRobotModelId(UUID.randomUUID());
        robot.setName(externalRobotId);
        robot.setSerialNumber(legacyVendorSerial);
        robot.setExternalRobotId(externalRobotId);
        return robot;
    }

    private void stubAnyModelWithCbPrefix() {
        RobotModel model = new RobotModel();
        model.setId(UUID.randomUUID());
        model.setSerialPrefix("CB");
        when(robotModelRepository.findById(any())).thenReturn(Optional.of(model));
    }

    // 1: five legacy robots are reconciled
    @Test
    void reconcile_fiveLegacyRobots_allReconciled() {
        List<Robot> robots = List.of(
                legacyRobot("94:BA:06:CA:99:F3", "QC402602X00002"),
                legacyRobot("94:BA:06:CA:9A:05", "QC402602X00004"),
                legacyRobot("94:BA:06:CA:9A:23", "QC402602X00005"),
                legacyRobot("88:49:2D:5F:29:55", "QS12603BX0002"),
                legacyRobot("A8:B5:8E:B5:E3:E7", "KRW325111W0031"));
        when(robotRepository.findByOrganizationId(ORG_ID)).thenReturn(robots);
        stubAnyModelWithCbPrefix();
        when(sakarSerialNumberService.nextSerialNumber(any())).thenReturn(
                "SR-CB-2026-000001", "SR-CB-2026-000002", "SR-CB-2026-000003",
                "SR-CB-2026-000004", "SR-CB-2026-000005");

        RobotSerialReconciliationResult result = service().reconcileLegacySerialNumbers(ORG_ID);

        assertThat(result.totalRobotsInScope()).isEqualTo(5);
        assertThat(result.reconciled()).isEqualTo(5);
        assertThat(result.alreadyReconciled()).isZero();
        assertThat(result.notApplicable()).isZero();
        verify(robotRepository, times(5)).save(any());
    }

    // 2 + 11: each reconciled robot receives a unique Sakar serial
    @Test
    void reconcile_eachRobot_receivesAUniqueSakarSerial() {
        List<Robot> robots = List.of(
                legacyRobot("robot-a", "MFT-A"),
                legacyRobot("robot-b", "MFT-B"),
                legacyRobot("robot-c", "MFT-C"));
        when(robotRepository.findByOrganizationId(ORG_ID)).thenReturn(robots);
        stubAnyModelWithCbPrefix();
        when(sakarSerialNumberService.nextSerialNumber(any())).thenReturn(
                "SR-CB-2026-000010", "SR-CB-2026-000011", "SR-CB-2026-000012");

        RobotSerialReconciliationResult result = service().reconcileLegacySerialNumbers(ORG_ID);

        List<String> newSerials = result.entries().stream().map(RobotSerialReconciliationResult.ReconciledEntry::newSerialNumber).toList();
        assertThat(newSerials).doesNotHaveDuplicates();
        assertThat(newSerials).allMatch(s -> s.matches("SR-CB-\\d{4}-\\d{6}"));
    }

    // 3: old serial_number is preserved as vendor_serial_number
    @Test
    void reconcile_oldSerialNumber_isPreservedAsVendorSerialNumber() {
        Robot robot = legacyRobot("94:BA:06:CA:99:F3", "QC402602X00002");
        when(robotRepository.findByOrganizationId(ORG_ID)).thenReturn(List.of(robot));
        stubAnyModelWithCbPrefix();
        when(sakarSerialNumberService.nextSerialNumber(any())).thenReturn("SR-CB-2026-000001");

        service().reconcileLegacySerialNumbers(ORG_ID);

        assertThat(robot.getVendorSerialNumber()).isEqualTo("QC402602X00002");
        assertThat(robot.getSerialNumber()).isEqualTo("SR-CB-2026-000001");
    }

    // 4 + 5 + 6 + 7: external_robot_id / robot UUID / organization_id / model are all unchanged
    @Test
    void reconcile_neverChangesIdentityFieldsOtherThanTheSerials() {
        Robot robot = legacyRobot("94:BA:06:CA:99:F3", "QC402602X00002");
        UUID originalId = robot.getId();
        UUID originalModelId = robot.getRobotModelId();
        UUID originalOrgId = robot.getOrganizationId();
        String originalName = robot.getName();
        robot.setStatus(RobotLifecycleStatus.ACTIVE);
        when(robotRepository.findByOrganizationId(ORG_ID)).thenReturn(List.of(robot));
        stubAnyModelWithCbPrefix();
        when(sakarSerialNumberService.nextSerialNumber(any())).thenReturn("SR-CB-2026-000001");

        service().reconcileLegacySerialNumbers(ORG_ID);

        assertThat(robot.getId()).isEqualTo(originalId);
        assertThat(robot.getExternalRobotId()).isEqualTo("94:BA:06:CA:99:F3");
        assertThat(robot.getRobotModelId()).isEqualTo(originalModelId);
        assertThat(robot.getOrganizationId()).isEqualTo(originalOrgId);
        assertThat(robot.getName()).isEqualTo(originalName);
        assertThat(robot.getStatus()).isEqualTo(RobotLifecycleStatus.ACTIVE);
    }

    // 8: second reconciliation is idempotent — no new serial is generated the second time
    @Test
    void reconcile_runTwice_secondRunIsANoOp() {
        Robot robot = legacyRobot("94:BA:06:CA:99:F3", "QC402602X00002");
        when(robotRepository.findByOrganizationId(ORG_ID)).thenReturn(List.of(robot));
        stubAnyModelWithCbPrefix();
        when(sakarSerialNumberService.nextSerialNumber(any())).thenReturn("SR-CB-2026-000001");

        RobotSerialReconciliationResult first = service().reconcileLegacySerialNumbers(ORG_ID);
        RobotSerialReconciliationResult second = service().reconcileLegacySerialNumbers(ORG_ID);

        assertThat(first.reconciled()).isEqualTo(1);
        assertThat(second.reconciled()).isZero();
        assertThat(second.alreadyReconciled()).isEqualTo(1);
        assertThat(robot.getSerialNumber()).isEqualTo("SR-CB-2026-000001");
        assertThat(robot.getVendorSerialNumber()).isEqualTo("QC402602X00002");
        verify(sakarSerialNumberService, times(1)).nextSerialNumber(any());
    }

    // 9: a manually-registered robot (no vendor identity) and an already-reconciled robot are both left untouched
    @Test
    void reconcile_nonLegacyRobots_areNeverModified() {
        Robot manuallyRegistered = legacyRobot(null, "ANY-SERIAL-1234");
        manuallyRegistered.setExternalRobotId(null); // never vendor-linked
        Robot alreadyReconciled = legacyRobot("94:BA:06:CA:9A:05", "SR-CB-2025-000099");
        alreadyReconciled.setSerialNumber("SR-CB-2025-000099");
        alreadyReconciled.setVendorSerialNumber("QC402602X00004");
        when(robotRepository.findByOrganizationId(ORG_ID)).thenReturn(List.of(manuallyRegistered, alreadyReconciled));

        RobotSerialReconciliationResult result = service().reconcileLegacySerialNumbers(ORG_ID);

        assertThat(result.reconciled()).isZero();
        assertThat(result.alreadyReconciled()).isEqualTo(1);
        assertThat(result.notApplicable()).isEqualTo(1);
        assertThat(manuallyRegistered.getSerialNumber()).isEqualTo("ANY-SERIAL-1234");
        verify(robotRepository, never()).save(any());
    }

    // 10: a robot with no external identity is safely excluded, never crashes the ordering/sort step
    @Test
    void reconcile_robotWithNoExternalRobotId_isExcludedSafely_neverThrows() {
        Robot noVendorIdentity = legacyRobot(null, "SOME-RAW-VALUE");
        noVendorIdentity.setExternalRobotId(null);
        Robot genuinelyLegacy = legacyRobot("94:BA:06:CA:99:F3", "QC402602X00002");
        when(robotRepository.findByOrganizationId(ORG_ID)).thenReturn(List.of(noVendorIdentity, genuinelyLegacy));
        stubAnyModelWithCbPrefix();
        when(sakarSerialNumberService.nextSerialNumber(any())).thenReturn("SR-CB-2026-000001");

        RobotSerialReconciliationResult result = service().reconcileLegacySerialNumbers(ORG_ID);

        assertThat(result.reconciled()).isEqualTo(1);
        assertThat(result.notApplicable()).isEqualTo(1);
        assertThat(noVendorIdentity.getSerialNumber()).isEqualTo("SOME-RAW-VALUE");
    }

    // 12: deterministic ordering — legacy robots are processed sorted by external_robot_id ascending
    @Test
    void reconcile_legacyRobots_areProcessedInExternalRobotIdAscendingOrder() {
        // Deliberately supplied out of order to prove the service sorts rather than relying on
        // list/row order.
        Robot robotZ = legacyRobot("Z-LAST", "MFT-Z");
        Robot robotA = legacyRobot("A-FIRST", "MFT-A");
        Robot robotM = legacyRobot("M-MIDDLE", "MFT-M");
        when(robotRepository.findByOrganizationId(ORG_ID)).thenReturn(List.of(robotZ, robotA, robotM));
        stubAnyModelWithCbPrefix();
        when(sakarSerialNumberService.nextSerialNumber(any())).thenReturn(
                "SR-CB-2026-000001", "SR-CB-2026-000002", "SR-CB-2026-000003");

        RobotSerialReconciliationResult result = service().reconcileLegacySerialNumbers(ORG_ID);

        assertThat(result.entries()).extracting(RobotSerialReconciliationResult.ReconciledEntry::externalRobotId)
                .containsExactly("A-FIRST", "M-MIDDLE", "Z-LAST");
        assertThat(result.entries()).extracting(RobotSerialReconciliationResult.ReconciledEntry::newSerialNumber)
                .containsExactly("SR-CB-2026-000001", "SR-CB-2026-000002", "SR-CB-2026-000003");
    }
}
