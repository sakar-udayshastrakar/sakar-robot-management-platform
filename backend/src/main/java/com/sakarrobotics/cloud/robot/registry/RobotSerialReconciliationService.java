package com.sakarrobotics.cloud.robot.registry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sakarrobotics.cloud.common.error.ApiException;
import com.sakarrobotics.cloud.common.error.SakarErrorCode;
import com.sakarrobotics.cloud.robot.registry.dto.RobotSerialReconciliationResult;
import com.sakarrobotics.cloud.robot.registry.dto.RobotSerialReconciliationResult.ReconciledEntry;

import lombok.RequiredArgsConstructor;

/**
 * One-time, explicitly-triggered data correction for robots created before the Seventeenth pass
 * introduced Sakar-generated serial numbers. Those earlier (Sixteenth-pass) records have {@code
 * serial_number} set to the vendor's own manufacturer serial (e.g. Keenon {@code mftCode}) and
 * {@code vendor_serial_number} left {@code null} — the exact opposite of the current, correct
 * identity model. This service moves each such robot to the correct shape:
 *
 * <pre>
 * BEFORE: serial_number = QC402602X00002, vendor_serial_number = NULL
 * AFTER:  serial_number = SR-CB-2026-000001, vendor_serial_number = QC402602X00002
 * </pre>
 *
 * <p><strong>Deliberately NOT wired to any automatic trigger:</strong> no {@code @PostConstruct},
 * no {@code @Scheduled}, and {@link KeenonRobotSyncService} does not call this — this is a
 * one-time correction for specific pre-existing rows, not an ongoing sync behavior. It runs only
 * when explicitly invoked (via {@code RobotSerialReconciliationController}), so implementing and
 * testing this class causes zero effect on any running deployment until that call is made.
 *
 * <p><strong>Legacy detection (never guesses):</strong> a robot is treated as legacy only if it is
 * vendor-linked ({@code externalRobotId != null} — a manually-registered robot with no vendor
 * identity is never touched), not yet reconciled ({@code vendorSerialNumber == null}), and its
 * {@code serialNumber} does not already look like a real Sakar serial. The only code path that
 * could ever have written a non-Sakar-format {@code serialNumber} onto a vendor-linked robot was
 * the (now-fixed) Sixteenth-pass sync, which used the vendor's own {@code mftCode} verbatim — so
 * that value IS the manufacturer serial to preserve, not an assumption. A robot that does not
 * meet all three conditions is left completely untouched and counted separately (never silently
 * skipped without being reported) — see {@link RobotSerialReconciliationResult}.
 *
 * <p><strong>Ordering:</strong> legacy robots are processed sorted by {@code externalRobotId}
 * ascending — the vendor's own immutable identifier — never database row/insertion order (not a
 * reproducible ordering) and never robot name (mutable, and Sixteenth-pass sync could fall back
 * to the vendor id itself when Keenon reported none). This makes which robot receives which
 * sequence number fully deterministic and reproducible across runs against the same data.
 *
 * <p><strong>Idempotency:</strong> a second run finds every previously-reconciled robot already
 * has a non-null {@code vendorSerialNumber}, so {@link #isLegacy} excludes it — no new serial is
 * ever generated for it.
 *
 * <p><strong>Serial generation:</strong> exclusively through the existing {@link
 * SakarSerialNumberService} (the same database-backed sequence every other new robot uses) —
 * never {@code MAX(serial_number) + 1}, never a hardcoded value.
 *
 * <p><strong>Transaction/uniqueness safety:</strong> the whole reconciliation is one {@code
 * @Transactional} operation — a failure partway through rolls back every change made so far,
 * never leaving a partially-migrated identity state. No temporary duplicate {@code
 * serial_number} is ever possible: each robot's old value moves to a different column (no
 * uniqueness constraint on {@code vendor_serial_number}) and its new value comes from an atomic
 * sequence increment, so two robots can never momentarily share a serial.
 */
@Service
@RequiredArgsConstructor
public class RobotSerialReconciliationService {

    // Matches any Sakar serial regardless of model-family prefix (SR-CB-..., SR-BT-..., SR-PL-...,
    // or any future one) — legacy detection must not assume one fixed prefix.
    private static final Pattern SAKAR_SERIAL_PATTERN = Pattern.compile("^SR-[A-Z]{2,6}-\\d{4}-\\d{6}$");

    private final RobotRepository robotRepository;
    private final RobotModelRepository robotModelRepository;
    private final SakarSerialNumberService sakarSerialNumberService;

    @Transactional
    public RobotSerialReconciliationResult reconcileLegacySerialNumbers(UUID organizationId) {
        List<Robot> robotsInScope = robotRepository.findByOrganizationId(organizationId);

        List<Robot> legacyRobots = robotsInScope.stream()
                .filter(RobotSerialReconciliationService::isLegacy)
                .sorted(Comparator.comparing(Robot::getExternalRobotId))
                .toList();

        // Snapshot classification BEFORE any mutation below — reconciling a legacy robot sets
        // its vendorSerialNumber, which would otherwise make it indistinguishable here from a
        // robot that was already reconciled before this run even started.
        int alreadyReconciled = (int) robotsInScope.stream()
                .filter(r -> r.getVendorSerialNumber() != null)
                .count();
        int notApplicable = robotsInScope.size() - legacyRobots.size() - alreadyReconciled;

        List<ReconciledEntry> reconciledEntries = new ArrayList<>();
        for (Robot robot : legacyRobots) {
            String legacyVendorSerial = robot.getSerialNumber();
            RobotModel model = robotModelRepository.findById(robot.getRobotModelId())
                    .orElseThrow(() -> new ApiException(SakarErrorCode.ROBOT_MODEL_NOT_FOUND,
                            "Robot model not found: " + robot.getRobotModelId()));
            String newSakarSerial = sakarSerialNumberService.nextSerialNumber(model);
            robot.setVendorSerialNumber(legacyVendorSerial);
            robot.setSerialNumber(newSakarSerial);
            robotRepository.save(robot);
            reconciledEntries.add(new ReconciledEntry(robot.getId(), robot.getExternalRobotId(), newSakarSerial, legacyVendorSerial));
        }

        return new RobotSerialReconciliationResult(robotsInScope.size(), reconciledEntries.size(), alreadyReconciled,
                notApplicable, reconciledEntries);
    }

    private static boolean isLegacy(Robot robot) {
        return robot.getExternalRobotId() != null
                && robot.getVendorSerialNumber() == null
                && robot.getSerialNumber() != null
                && !SAKAR_SERIAL_PATTERN.matcher(robot.getSerialNumber()).matches();
    }
}
