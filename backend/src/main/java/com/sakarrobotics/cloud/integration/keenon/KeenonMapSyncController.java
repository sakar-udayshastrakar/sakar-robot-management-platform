package com.sakarrobotics.cloud.integration.keenon;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sakarrobotics.cloud.common.error.ApiException;
import com.sakarrobotics.cloud.common.error.SakarErrorCode;
import com.sakarrobotics.cloud.common.web.ApiResponse;
import com.sakarrobotics.cloud.map.RobotMap;
import com.sakarrobotics.cloud.map.RobotMapResponse;
import com.sakarrobotics.cloud.robot.registry.AdapterType;
import com.sakarrobotics.cloud.robot.registry.Robot;
import com.sakarrobotics.cloud.robot.registry.RobotModel;
import com.sakarrobotics.cloud.robot.registry.RobotModelRepository;
import com.sakarrobotics.cloud.robot.registry.RobotService;
import com.sakarrobotics.cloud.security.UserPrincipal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * Manual/on-demand map metadata + PNG image sync — same "no way for an
 * operator to sync on demand" gap and shape as {@link
 * KeenonAreaSyncController}/{@link KeenonCleaningHistorySyncController}
 * (today {@link KeenonMapImageSyncScheduler} only ever runs on its own
 * disabled-by-default interval). Needs no request body at all — unlike area/
 * cleaning-history sync's {@code storeId}, a robot's sceneCode now comes
 * entirely from its own {@link KeenonRobotSceneConfig} row (see {@link
 * KeenonMapMetadataSyncService}'s Javadoc), never a per-call parameter.
 *
 * <p>Deliberately NOT gated on {@link
 * com.sakarrobotics.cloud.robot.registry.RobotCapabilityType#GET_MAP} — same
 * reason as {@code RobotController#map}: that capability is false for the
 * C40 S model in seed data (real map-image retrieval via {@code
 * KeenonRobotAdapter#getMap} remains a separate, unimplemented concept),
 * which would make this endpoint permanently unusable for exactly the
 * robots it exists to serve.
 */
@RestController
@RequestMapping("/api/v1/robots/{robotId}/keenon/map")
@RequiredArgsConstructor
@Tag(name = "Keenon Map Sync")
public class KeenonMapSyncController {

    private final RobotService robotService;
    private final RobotModelRepository robotModelRepository;
    private final KeenonMapImageSyncService keenonMapImageSyncService;

    @PostMapping("/sync")
    @PreAuthorize("hasAuthority('ROBOT_CONFIGURE')")
    @Operation(summary = "Sync this robot's map metadata and PNG image from Keenon, using its "
            + "Sakar-configured sceneCode (see PUT .../keenon/scene-config) — KEENON_CLOUD robots "
            + "only. Returns RESOURCE_NOT_FOUND if no sceneCode has been configured for this robot yet.")
    public ApiResponse<RobotMapResponse> sync(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID robotId) {
        Robot robot = robotService.getAccessibleOrThrow(principal, robotId);

        RobotModel model = robotModelRepository.findById(robot.getRobotModelId())
                .orElseThrow(() -> new ApiException(SakarErrorCode.ROBOT_MODEL_NOT_FOUND, "Robot model not found"));
        if (model.getAdapterType() != AdapterType.KEENON_CLOUD) {
            throw new ApiException(SakarErrorCode.UNSUPPORTED_CAPABILITY,
                    "Keenon map sync is only supported for KEENON_CLOUD robots");
        }

        RobotMap robotMap = keenonMapImageSyncService.sync(robot)
                .orElseThrow(() -> new ApiException(SakarErrorCode.RESOURCE_NOT_FOUND,
                        "No Keenon scene configured for robot " + robotId));
        return ApiResponse.ok(RobotMapResponse.from(robotMap));
    }
}
