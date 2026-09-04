package com.sakarrobotics.cloud.integration.keenon;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sakarrobotics.cloud.common.error.ApiException;
import com.sakarrobotics.cloud.common.error.SakarErrorCode;
import com.sakarrobotics.cloud.map.RobotMap;
import com.sakarrobotics.cloud.map.RobotMapRepository;
import com.sakarrobotics.cloud.robot.registry.Robot;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.JsonNode;

/**
 * Populates the existing (previously schema-only) {@code maps} table /
 * {@link RobotMap} entity with real, evidenced map-metadata fields —
 * deliberately NOT the actual Keenon map-image endpoint.
 *
 * <p><strong>Why this endpoint, not {@code GET .../custom/robot/map}:
 * </strong> the real Keenon map-image endpoint requires {@code sceneCode}
 * and {@code floorInfo} query parameters, and returns an embedded base64
 * PNG blob plus named 3D points — real geometry. No mechanism anywhere in
 * this codebase resolves a per-robot {@code sceneCode}/{@code floorInfo}
 * pair (unlike {@code storeId}, which area/cleaning-history sync can reuse
 * from an existing {@code KeenonAreaMapping} row), and there is nowhere
 * safe to store or serve an embedded image blob as a real URL without new,
 * out-of-scope file-storage infrastructure. {@link
 * KeenonRobotAdapter#getMap} remains the existing, deliberate
 * {@code FEATURE_NOT_YET_IMPLEMENTED} stub for exactly this reason — this
 * slice does not touch it or try to work around the gap.
 *
 * <p><strong>What IS evidenced:</strong> {@code sceneCode}/{@code
 * sceneName} are documented, live-confirmed fields on the robot-status
 * response itself ({@code GET /api/open/scene/v1/robot/status}) — the same
 * call {@link KeenonRobotAdapter#getStatus} and {@link
 * KeenonStatusSyncService} already make, just reading two fields neither
 * of them currently extracts. This is the one genuinely evidenced,
 * per-robot "which map/location is this robot in" signal available —
 * mapped onto {@code vendor_map_id}/{@code name} on the existing schema.
 * Keenon's own internal {@code mapId} concept (as distinct from scene) has
 * never been confirmed to exist in any live response this project has
 * evidence for (see {@code KeenonAreaMapping.keenonMapId}'s own Javadoc,
 * which notes the same gap for a different endpoint).
 *
 * <p>{@code image_url} is never populated — no real URL is ever evidenced,
 * and one is never fabricated. {@code map_points}/geometry is never
 * touched by this service at all.
 */
@Service
@RequiredArgsConstructor
public class KeenonMapMetadataSyncService {

    private final KeenonApiClient client;
    private final RobotMapRepository robotMapRepository;

    @Transactional
    public Optional<RobotMap> sync(Robot robot) {
        if (robot.getExternalRobotId() == null) {
            throw new ApiException(SakarErrorCode.INTEGRATION_UNAVAILABLE,
                    "Robot " + robot.getId() + " has no external (Keenon) identifier configured");
        }

        JsonNode response = client.getRobotStatus(robot.getExternalRobotId());
        JsonNode data = response != null ? response.get("data") : null;
        String sceneCode = textOrNull(data, "sceneCode");
        if (sceneCode == null) {
            // No new map/scene information in this response — never overwrite a
            // previously-known-good value with null just because this particular
            // call didn't report one.
            return Optional.empty();
        }
        String sceneName = textOrNull(data, "sceneName");

        RobotMap map = robotMapRepository.findByRobotId(robot.getId()).orElseGet(RobotMap::new);
        map.setRobotId(robot.getId());
        map.setVendorMapId(sceneCode);
        // name is nullable on this entity (unlike the NOT NULL display_name columns on the
        // other Keenon mapping tables) — fall back to the scene code only if Keenon reports
        // no name, still never inventing one.
        map.setName(sceneName != null ? sceneName : sceneCode);
        return Optional.of(robotMapRepository.save(map));
    }

    private static String textOrNull(JsonNode node, String field) {
        return node != null && node.hasNonNull(field) ? node.get(field).asText() : null;
    }
}
