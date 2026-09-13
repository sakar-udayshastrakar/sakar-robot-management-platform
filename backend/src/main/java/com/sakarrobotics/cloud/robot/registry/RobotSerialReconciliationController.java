package com.sakarrobotics.cloud.robot.registry;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sakarrobotics.cloud.common.web.ApiResponse;
import com.sakarrobotics.cloud.robot.registry.dto.ReconcileLegacySerialsRequest;
import com.sakarrobotics.cloud.robot.registry.dto.RobotSerialReconciliationResult;
import com.sakarrobotics.cloud.security.UserPrincipal;
import com.sakarrobotics.cloud.security.access.TenantAccessGuard;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Deliberately separate from {@link RobotController} (a bulk, cross-robot administrative action,
 * not a single-robot read/write) and kept in this vendor-neutral package rather than {@code
 * integration.keenon} — {@link RobotSerialReconciliationService}'s own logic and this endpoint's
 * request/response contain no vendor-specific field despite existing to fix up data the Keenon
 * sync (Sixteenth pass) originally got wrong.
 *
 * <p>Sakar-database-only: never calls a vendor API, never sends a robot command. See {@link
 * RobotSerialReconciliationService}'s Javadoc for why this is explicitly NOT wired to any
 * automatic trigger — it only runs when this endpoint is called.
 */
@RestController
@RequestMapping("/api/v1/robots")
@RequiredArgsConstructor
@Tag(name = "Robot Serial Reconciliation")
public class RobotSerialReconciliationController {

    private final RobotSerialReconciliationService reconciliationService;
    private final TenantAccessGuard tenantAccessGuard;

    @PostMapping("/reconcile-legacy-serials")
    @PreAuthorize("hasAuthority('ROBOT_CONFIGURE')")
    @Operation(summary = "One-time, Sakar-database-only reconciliation: a legacy robot whose "
            + "serial_number still holds its vendor manufacturer serial (e.g. Keenon mftCode) "
            + "receives a real Sakar-generated serial (SR-CB-YYYY-NNNNNN) via the existing "
            + "SakarSerialNumberService; the old value is preserved as vendor_serial_number. "
            + "Idempotent — an already-reconciled or non-vendor-linked robot is left untouched. "
            + "Never calls a vendor API, never sends a robot command.")
    public ApiResponse<RobotSerialReconciliationResult> reconcileLegacySerials(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ReconcileLegacySerialsRequest request) {
        tenantAccessGuard.assertOrganizationAccess(principal, request.organizationId());
        return ApiResponse.ok(reconciliationService.reconcileLegacySerialNumbers(request.organizationId()));
    }
}
