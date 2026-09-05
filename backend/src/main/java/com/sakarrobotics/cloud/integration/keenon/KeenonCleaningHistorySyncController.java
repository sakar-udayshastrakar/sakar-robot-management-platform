package com.sakarrobotics.cloud.integration.keenon;

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
import com.sakarrobotics.cloud.integration.keenon.dto.CleaningHistorySyncResponse;
import com.sakarrobotics.cloud.integration.keenon.dto.SyncCleaningHistoryRequest;
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
 * Manual/on-demand counterpart to {@link KeenonCleaningHistorySyncScheduler} —
 * that scheduler only ever runs on its own interval and is disabled by
 * default, so today there is no way for an operator to sync one robot's
 * cleaning history on demand. Follows the same "vendor integration boundary"
 * exemption and shape as {@link KeenonAreaSyncController} (this endpoint's
 * own request is inherently vendor-specific — a Keenon {@code storeId} — so
 * it lives alongside the other Keenon admin-action controllers rather than
 * in the vendor-neutral {@code RobotController}), and delegates directly to
 * the already-implemented, already-tested {@link
 * KeenonCleaningHistorySyncService#sync} — no new sync/mapping/dedup logic
 * here.
 */
@RestController
@RequestMapping("/api/v1/robots/{robotId}/keenon/cleaning-history")
@RequiredArgsConstructor
@Tag(name = "Keenon Cleaning History Sync")
public class KeenonCleaningHistorySyncController {

    private final RobotService robotService;
    private final RobotModelRepository robotModelRepository;
    private final RobotCapabilityService robotCapabilityService;
    private final KeenonCleaningHistorySyncService keenonCleaningHistorySyncService;

    @PostMapping("/sync")
    @PreAuthorize("hasAuthority('ROBOT_CONFIGURE')")
    @Operation(summary = "Sync this robot's cleaning-session history from Keenon's own clean/log/list into "
            + "cleaning_sessions, given an explicitly supplied Keenon store id (never hardcoded, never "
            + "inferred) — KEENON_CLOUD robots only. Returns the count of new (post-dedup) rows appended "
            + "this call; zero is a normal result, not an error.")
    public ApiResponse<CleaningHistorySyncResponse> sync(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID robotId,
            @Valid @RequestBody SyncCleaningHistoryRequest request) {
        Robot robot = robotService.getAccessibleOrThrow(principal, robotId);
        robotCapabilityService.assertSupported(robot.getRobotModelId(), RobotCapabilityType.CLEANING);

        RobotModel model = robotModelRepository.findById(robot.getRobotModelId())
                .orElseThrow(() -> new ApiException(SakarErrorCode.ROBOT_MODEL_NOT_FOUND, "Robot model not found"));
        if (model.getAdapterType() != AdapterType.KEENON_CLOUD) {
            throw new ApiException(SakarErrorCode.UNSUPPORTED_CAPABILITY,
                    "Keenon cleaning-history sync is only supported for KEENON_CLOUD robots");
        }

        int newRecords = keenonCleaningHistorySyncService.sync(robot, request.storeId());
        return ApiResponse.ok(new CleaningHistorySyncResponse(newRecords));
    }
}
