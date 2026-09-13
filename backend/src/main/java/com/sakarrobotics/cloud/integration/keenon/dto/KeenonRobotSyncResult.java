package com.sakarrobotics.cloud.integration.keenon.dto;

import java.util.List;

/**
 * Result of one {@code KeenonRobotSyncService.sync} run. {@code discovered}
 * is however many vendor records the response actually contained;
 * {@code created + updated + unchanged + failed} always equals it. {@link
 * Failure#vendorRobotId()} is {@code null} only for a vendor record that had
 * no {@code robotId} at all (nothing to report); every failure reason is
 * this codebase's own {@code ApiException} message — never a raw vendor
 * payload or credential.
 */
public record KeenonRobotSyncResult(
        int discovered,
        int created,
        int updated,
        int unchanged,
        int failed,
        List<Failure> failures) {

    public record Failure(String vendorRobotId, String reason) {
    }
}
