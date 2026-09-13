package com.sakarrobotics.cloud.integration.keenon;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sakarrobotics.cloud.common.web.ApiResponse;
import com.sakarrobotics.cloud.integration.keenon.dto.KeenonRobotSyncResult;
import com.sakarrobotics.cloud.integration.keenon.dto.SyncRobotsRequest;
import com.sakarrobotics.cloud.security.UserPrincipal;
import com.sakarrobotics.cloud.security.access.TenantAccessGuard;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Vendor-specific administrative action (Keenon robot-discovery slice) —
 * lives alongside {@link KeenonAreaSyncController}/{@link
 * KeenonWebhookController} for the same "vendor integration boundary"
 * reason: the request (a Keenon {@code storeId}) is inherently
 * vendor-specific, but this IS a normal Sakar-user-facing, JWT/RBAC/tenant-
 * scoped endpoint, not a vendor-authenticated callback.
 *
 * <p>Unlike area/back-point sync, this endpoint has no already-registered
 * robot to scope a path variable to — discovery can create brand-new
 * robots — so the caller supplies the target {@code organizationId}
 * explicitly, exactly as {@code POST /api/v1/robots} (registration)
 * already requires.
 */
@RestController
@RequestMapping("/api/v1/keenon/robots")
@RequiredArgsConstructor
@Tag(name = "Keenon Robot Sync")
public class KeenonRobotSyncController {

    private final KeenonRobotSyncService keenonRobotSyncService;
    private final TenantAccessGuard tenantAccessGuard;

    @PostMapping("/sync")
    @PreAuthorize("hasAuthority('ROBOT_CONFIGURE')")
    @Operation(summary = "Discover every robot the configured Keenon Cloud account can see for a given "
            + "store, and idempotently create/update the corresponding Sakar robots/robot_models. "
            + "Read-only Keenon discovery call only — never sends a physical robot command.")
    public ApiResponse<KeenonRobotSyncResult> sync(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody SyncRobotsRequest request) {
        tenantAccessGuard.assertOrganizationAccess(principal, request.organizationId());
        return ApiResponse.ok(keenonRobotSyncService.sync(request.organizationId(), request.storeId()));
    }
}
