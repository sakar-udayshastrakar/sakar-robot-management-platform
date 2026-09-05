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

/**
 * Populates the existing (previously schema-only) {@code maps} table /
 * {@link RobotMap} entity with real, evidenced map-metadata fields.
 *
 * <p><strong>Why sceneCode is Sakar-owned configuration, not a live Keenon
 * read (Phase 1I):</strong> live verification proved {@code GET
 * /api/open/custom/clean/robot/status} — the only status endpoint that
 * currently works for this account on C-series robots — does not return
 * {@code sceneCode}/{@code sceneName}/{@code mapId} at all (a real captured
 * response is pinned in {@code KeenonRobotAdapterTest}'s {@code
 * LIVE_CLEANING_STATUS_RESPONSE} fixture). The one endpoint that ever did
 * return {@code sceneCode} ({@code GET /api/open/scene/v1/robot/status}) is
 * rejected with 610403 ("insufficient permission") for this account on
 * C-series robots (see {@link KeenonApiClient#getRobotStatus}'s own
 * Javadoc). No other live-tested endpoint resolves a robot's CURRENT
 * sceneCode: {@code GET /api/open/custom/clean/robot/area/list} returns a
 * robot-specific {@code mapId}, but that is a differently-shaped identifier
 * (32-character hex vs. {@code sceneCode}'s 6-character mixed-case code)
 * never confirmed interchangeable with the {@code sceneCode} parameter
 * {@link KeenonApiClient#getMapData}/{@link KeenonApiClient#getMapPosition}
 * require; {@code GET /api/open/scene/v1/info/list} is store-wide and
 * cannot alone attribute a scene to any one robot.
 *
 * <p>Until Keenon documents/exposes an authoritative robot -&gt; scene
 * endpoint, {@link KeenonRobotSceneConfig} — one row per robot, set only via
 * {@code PUT /api/v1/robots/{id}/keenon/scene-config} — is the sole source
 * of a robot's sceneCode: Sakar-owned configuration data, never a global
 * default, never inferred from another robot's configuration, and never
 * defaulted to a historical sceneCode or a vendor {@code mapId}. A robot
 * with no configured row resolves to {@link Optional#empty()}, exactly like
 * "no scene reported" did before this change — never a fabricated value.
 *
 * <p>{@code image_url} is never populated here — no real URL is ever
 * evidenced, and one is never fabricated. {@code map_points}/geometry is
 * never touched by this service at all.
 */
@Service
@RequiredArgsConstructor
public class KeenonMapMetadataSyncService {

    private final KeenonRobotSceneConfigRepository sceneConfigRepository;
    private final RobotMapRepository robotMapRepository;

    @Transactional
    public Optional<RobotMap> sync(Robot robot) {
        if (robot.getExternalRobotId() == null) {
            throw new ApiException(SakarErrorCode.INTEGRATION_UNAVAILABLE,
                    "Robot " + robot.getId() + " has no external (Keenon) identifier configured");
        }

        Optional<KeenonRobotSceneConfig> configuredScene = sceneConfigRepository.findByRobotId(robot.getId());
        if (configuredScene.isEmpty()) {
            // No Sakar-configured sceneCode for this robot — never fabricated, never
            // inferred from another robot's configuration, a historical sceneCode, or a
            // vendor mapId (see this class's own Javadoc for the full evidence trail).
            return Optional.empty();
        }
        String sceneCode = configuredScene.get().getSceneCode();
        String sceneName = configuredScene.get().getSceneName();

        RobotMap map = robotMapRepository.findByRobotId(robot.getId()).orElseGet(RobotMap::new);
        map.setRobotId(robot.getId());
        map.setVendorMapId(sceneCode);
        // name is nullable on this entity (unlike the NOT NULL display_name columns on the
        // other Keenon mapping tables) — fall back to the scene code only if no operator-
        // supplied name exists, still never inventing one.
        map.setName(sceneName != null ? sceneName : sceneCode);
        return Optional.of(robotMapRepository.save(map));
    }
}
