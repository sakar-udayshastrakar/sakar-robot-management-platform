package com.sakarrobotics.cloud.integration.keenon;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sakarrobotics.cloud.common.error.ApiException;
import com.sakarrobotics.cloud.common.error.SakarErrorCode;
import com.sakarrobotics.cloud.common.web.ApiResponse;
import com.sakarrobotics.cloud.integration.keenon.dto.SceneConfigResponse;
import com.sakarrobotics.cloud.integration.keenon.dto.SetSceneConfigRequest;
import com.sakarrobotics.cloud.robot.registry.AdapterType;
import com.sakarrobotics.cloud.robot.registry.Robot;
import com.sakarrobotics.cloud.robot.registry.RobotModel;
import com.sakarrobotics.cloud.robot.registry.RobotModelRepository;
import com.sakarrobotics.cloud.robot.registry.RobotService;
import com.sakarrobotics.cloud.security.UserPrincipal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Sakar-owned Keenon {@code sceneCode} configuration (Phase 1I) — follows the
 * same "vendor integration boundary" shape as {@link KeenonAreaSyncController}
 * / {@link KeenonCleaningHistorySyncController}, but this endpoint never
 * calls Keenon at all: it only reads/writes {@link KeenonRobotSceneConfig},
 * the sole current source of a robot's sceneCode (see {@link
 * KeenonMapMetadataSyncService}'s Javadoc for why no live vendor endpoint can
 * supply one for this account).
 */
@RestController
@RequestMapping("/api/v1/robots/{robotId}/keenon/scene-config")
@RequiredArgsConstructor
@Tag(name = "Keenon Scene Config")
public class KeenonSceneConfigController {

    private final RobotService robotService;
    private final RobotModelRepository robotModelRepository;
    private final KeenonRobotSceneConfigService sceneConfigService;

    @PutMapping
    @PreAuthorize("hasAuthority('ROBOT_CONFIGURE')")
    @Operation(summary = "Set this robot's Keenon sceneCode — Sakar-owned configuration data, never "
            + "read from a live Keenon endpoint. One robot's sceneCode is never inferred from or "
            + "defaulted to another robot's — KEENON_CLOUD robots only.")
    public ApiResponse<SceneConfigResponse> setSceneConfig(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID robotId,
            @Valid @RequestBody SetSceneConfigRequest request) {
        Robot robot = robotService.getAccessibleOrThrow(principal, robotId);
        assertKeenonCloud(robot);

        KeenonRobotSceneConfig saved = sceneConfigService.upsert(robot, request.sceneCode(), request.sceneName());
        return ApiResponse.ok(SceneConfigResponse.from(saved));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ROBOT_VIEW')")
    @Operation(summary = "Get this robot's configured Keenon sceneCode, if any. Returns "
            + "RESOURCE_NOT_FOUND if no sceneCode has been configured yet.")
    public ApiResponse<SceneConfigResponse> getSceneConfig(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID robotId) {
        Robot robot = robotService.getAccessibleOrThrow(principal, robotId);
        assertKeenonCloud(robot);

        KeenonRobotSceneConfig config = sceneConfigService.find(robot.getId())
                .orElseThrow(() -> new ApiException(SakarErrorCode.RESOURCE_NOT_FOUND,
                        "No Keenon scene configured for robot " + robotId));
        return ApiResponse.ok(SceneConfigResponse.from(config));
    }

    private void assertKeenonCloud(Robot robot) {
        RobotModel model = robotModelRepository.findById(robot.getRobotModelId())
                .orElseThrow(() -> new ApiException(SakarErrorCode.ROBOT_MODEL_NOT_FOUND, "Robot model not found"));
        if (model.getAdapterType() != AdapterType.KEENON_CLOUD) {
            throw new ApiException(SakarErrorCode.UNSUPPORTED_CAPABILITY,
                    "Keenon scene configuration is only supported for KEENON_CLOUD robots");
        }
    }
}
