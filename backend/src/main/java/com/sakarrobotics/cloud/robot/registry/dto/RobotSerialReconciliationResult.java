package com.sakarrobotics.cloud.robot.registry.dto;

import java.util.List;
import java.util.UUID;

/**
 * Result of one {@code RobotSerialReconciliationService.reconcileLegacySerialNumbers} run,
 * scoped to a single organization. {@code totalRobotsInScope} always equals {@code reconciled +
 * alreadyReconciled + notApplicable}. Running this twice in a row on the same data must produce
 * {@code reconciled = 0} on the second run — every previously-reconciled robot moves into {@code
 * alreadyReconciled} instead.
 */
public record RobotSerialReconciliationResult(
        int totalRobotsInScope,
        int reconciled,
        int alreadyReconciled,
        int notApplicable,
        List<ReconciledEntry> entries) {

    /** One robot whose {@code serial_number} was just replaced with a real Sakar serial. */
    public record ReconciledEntry(UUID robotId, String externalRobotId, String newSerialNumber, String vendorSerialNumber) {
    }
}
