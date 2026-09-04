package com.sakarrobotics.cloud.integration.keenon;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.StreamSupport;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sakarrobotics.cloud.common.error.ApiException;
import com.sakarrobotics.cloud.common.error.SakarErrorCode;
import com.sakarrobotics.cloud.map.MapPoint;
import com.sakarrobotics.cloud.map.MapPointRepository;
import com.sakarrobotics.cloud.map.RobotMap;
import com.sakarrobotics.cloud.robot.registry.Robot;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.JsonNode;

/**
 * Populates {@code map_points} from Keenon's own live
 * {@code GET .../custom/robot/map/position} — reuses {@link
 * KeenonMapMetadataSyncService} to resolve/refresh the robot's current
 * {@code sceneCode} (never duplicating that logic), then syncs the
 * returned {@code targetList[]} into {@link MapPoint} rows under that
 * {@link RobotMap}.
 *
 * <p><strong>Deliberately does not call {@code GET .../custom/robot/map}
 * </strong> (the map-image endpoint) — its only response field with any
 * schema equivalent is {@code content} (a base64 PNG), which {@link
 * RobotMap#getImageUrl()} is explicitly left {@code null} for in this
 * slice (no Sakar-owned image/object storage exists yet — a live call
 * whose entire result would be discarded is not made). Map-image
 * persistence is a separate future slice.
 *
 * <p><strong>{@code floorInfo} limitation:</strong> no Sakar mechanism
 * resolves a robot's actual floor — it is a configured default ({@code
 * sakar.integration.keenon.map-point-sync.default-floor-info}), the one
 * value empirically observed to work across every scene tested so far
 * (never derived from vendor-confirmed evidence for a specific scene).
 * Never hardcoded in code — always read from configuration.
 *
 * <p><strong>Upsert identity limitation:</strong> {@code map_points} has no
 * vendor point-id column, so {@code (mapId, name)} is the closest
 * schema-supported approximation of vendor identity (see {@link
 * com.sakarrobotics.cloud.map.MapPointRepository#findByMapIdAndName}). A
 * vendor-side rename of a point creates a new row rather than updating the
 * existing one — documented, not silently accepted as equivalent to true
 * id-based identity.
 *
 * <p>Charging points are identified dynamically from the vendor's own
 * {@code type == "charge"} field — never a hardcoded {@code targetId}.
 */
@Service
@RequiredArgsConstructor
public class KeenonMapPointSyncService {

    private final KeenonApiClient client;
    private final KeenonMapMetadataSyncService keenonMapMetadataSyncService;
    private final MapPointRepository mapPointRepository;

    @Value("${sakar.integration.keenon.map-point-sync.default-floor-info:1}")
    private String defaultFloorInfo;

    @Transactional
    public List<MapPoint> sync(Robot robot) {
        if (robot.getExternalRobotId() == null) {
            throw new ApiException(SakarErrorCode.INTEGRATION_UNAVAILABLE,
                    "Robot " + robot.getId() + " has no external (Keenon) identifier configured");
        }

        Optional<RobotMap> map = keenonMapMetadataSyncService.sync(robot);
        if (map.isEmpty()) {
            // No current sceneCode this run (e.g. robot offline) — nothing to sync points
            // against; never invent a scene, never touch existing points either.
            return List.of();
        }
        RobotMap robotMap = map.get();

        JsonNode response = client.getMapPosition(robotMap.getVendorMapId(), defaultFloorInfo);
        JsonNode list = response != null ? response.get("data") : null;
        JsonNode targetList = list != null ? list.get("targetList") : null;
        List<JsonNode> vendorPoints = targetList != null && targetList.isArray()
                ? StreamSupport.stream(targetList.spliterator(), false).toList()
                : List.of();

        Set<String> seenNames = new HashSet<>();
        List<MapPoint> result = new ArrayList<>();

        for (JsonNode node : vendorPoints) {
            String name = textOrNull(node, "name");
            if (name == null) {
                // No stable identity to upsert on at all — never invent one, just skip it.
                continue;
            }
            seenNames.add(name);

            MapPoint point = mapPointRepository.findByMapIdAndName(robotMap.getId(), name).orElseGet(MapPoint::new);
            point.setMapId(robotMap.getId());
            point.setName(name);
            // Raw vendor value, verbatim — never normalized/translated, so "charge" always
            // means exactly what the vendor reported, never a Sakar-invented category.
            point.setPointType(textOrNull(node, "type"));
            point.setX(doubleOrNull(node, "positionX"));
            point.setY(doubleOrNull(node, "positionY"));
            point.setActive(true);
            result.add(mapPointRepository.save(point));
        }

        // Deactivate only what THIS successful response's own absence proves gone — an empty
        // successful targetList deactivates every previously-active point for this map.
        for (MapPoint existingPoint : mapPointRepository.findByMapIdAndActiveTrue(robotMap.getId())) {
            if (!seenNames.contains(existingPoint.getName())) {
                existingPoint.setActive(false);
                mapPointRepository.save(existingPoint);
            }
        }

        return result;
    }

    private static String textOrNull(JsonNode node, String field) {
        return node != null && node.hasNonNull(field) ? node.get(field).asText() : null;
    }

    private static Double doubleOrNull(JsonNode node, String field) {
        return node != null && node.hasNonNull(field) ? node.get(field).asDouble() : null;
    }
}
