package com.sakarrobotics.cloud.integration.keenon;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sakarrobotics.cloud.common.web.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * Manual/on-demand trigger for {@link KeenonSyncOrchestrationService},
 * useful for testing the automatic 15-minute cycle without waiting for the
 * next scheduled boundary. Not a replacement for the schedule, and not a
 * frontend feature — no UI calls this.
 *
 * <p>Gated on {@code SYSTEM_ADMIN}, not the per-robot {@code
 * ROBOT_CONFIGURE} every other Keenon sync endpoint uses: unlike those
 * (each scoped to one robot the caller already has tenant access to, via
 * {@code robotService.getAccessibleOrThrow}), this endpoint runs one cycle
 * across every KEENON_CLOUD robot in every organization at once — exactly
 * what the underlying orchestrated schedulers already do — so it needs an
 * instance-wide permission, not a tenant-scoped one.
 */
@RestController
@RequestMapping("/api/v1/keenon/sync")
@RequiredArgsConstructor
@Tag(name = "Keenon Sync Orchestration")
public class KeenonSyncOrchestrationController {

    private final KeenonSyncOrchestrationService keenonSyncOrchestrationService;

    @PostMapping("/all")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN')")
    @Operation(summary = "Manually run one Keenon sync cycle immediately — the same cycle the 15-minute "
            + "scheduler runs (robot status, areas, cleaning modes, back points, cleaning history, map "
            + "metadata/points/images) for every already-registered KEENON_CLOUD robot across every "
            + "organization. Never discovers new robots — see POST /api/v1/keenon/robots/sync for that, "
            + "separately, which requires an explicit organizationId/storeId. Intended for testing; the "
            + "automatic schedule is the primary mechanism.")
    public ApiResponse<KeenonSyncOrchestrationService.SyncCycleResult> syncNow() {
        return ApiResponse.ok(keenonSyncOrchestrationService.syncAll());
    }
}
