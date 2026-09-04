package com.sakarrobotics.cloud.integration.keenon;

import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sakarrobotics.cloud.common.error.ApiException;
import com.sakarrobotics.cloud.common.error.SakarErrorCode;
import com.sakarrobotics.cloud.common.web.ApiResponse;
import com.sakarrobotics.cloud.integration.keenon.dto.SyncAreasRequest;
import com.sakarrobotics.cloud.robot.adapter.dto.AreaInfo;
import com.sakarrobotics.cloud.robot.registry.AdapterType;
import com.sakarrobotics.cloud.robot.registry.Robot;
import com.sakarrobotics.cloud.robot.registry.RobotCapabilityService;
import com.sakarrobotics.cloud.robot.registry.RobotCapabilityType;
import com.sakarrobotics.cloud.robot.registry.RobotModel;
import com.sakarrobotics.cloud.robot.registry.RobotModelRepository;
import com.sakarrobotics.cloud.robot.registry.RobotService;
import com.sakarrobotics.cloud.security.UserPrincipal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Vendor-specific administrative action (Keenon area-sync audit/slice) —
 * deliberately kept out of {@code RobotController}, whose own class Javadoc
 * requires every response there stay vendor-neutral. This endpoint's own
 * request (a Keenon {@code storeId}) is inherently vendor-specific, so it
 * lives alongside {@link KeenonWebhookController} instead, following the
 * same "vendor integration boundary" exemption
 * (SAKAR_ROBOT_PLATFORM_API_SPEC.md) — but unlike that controller, this one
 * IS a normal Sakar-user-facing, JWT/RBAC/tenant-scoped endpoint, not a
 * vendor-authenticated callback.
 */
@RestController
@RequestMapping("/api/v1/robots/{robotId}/keenon/areas")
@RequiredArgsConstructor
@Tag(name = "Keenon Area Sync")
public class KeenonAreaSyncController {

    private final RobotService robotService;
    private final RobotModelRepository robotModelRepository;
    private final RobotCapabilityService robotCapabilityService;
    private final KeenonAreaSyncService keenonAreaSyncService;

    @PostMapping("/sync")
    @PreAuthorize("hasAuthority('ROBOT_CONFIGURE')")
    @Operation(summary = "Sync this robot's cleanable areas from Keenon's own live area list into "
            + "keenon_area_mappings, given an explicitly supplied Keenon store id (never hardcoded, "
            + "never inferred) — KEENON_CLOUD robots only. Returns the resulting areas in the same "
            + "vendor-neutral shape as GET /robots/{id}/areas.")
    public ApiResponse<List<AreaInfo>> sync(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID robotId,
            @Valid @RequestBody SyncAreasRequest request) {
        Robot robot = robotService.getAccessibleOrThrow(principal, robotId);
        robotCapabilityService.assertSupported(robot.getRobotModelId(), RobotCapabilityType.GET_AREAS);

        RobotModel model = robotModelRepository.findById(robot.getRobotModelId())
                .orElseThrow(() -> new ApiException(SakarErrorCode.ROBOT_MODEL_NOT_FOUND, "Robot model not found"));
        if (model.getAdapterType() != AdapterType.KEENON_CLOUD) {
            throw new ApiException(SakarErrorCode.UNSUPPORTED_CAPABILITY,
                    "Keenon area sync is only supported for KEENON_CLOUD robots");
        }

        return ApiResponse.ok(keenonAreaSyncService.sync(robot, request.storeId()));
    }
}
