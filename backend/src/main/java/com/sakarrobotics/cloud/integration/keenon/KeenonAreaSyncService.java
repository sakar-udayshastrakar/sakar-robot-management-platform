package com.sakarrobotics.cloud.integration.keenon;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.StreamSupport;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sakarrobotics.cloud.common.error.ApiException;
import com.sakarrobotics.cloud.common.error.SakarErrorCode;
import com.sakarrobotics.cloud.robot.adapter.dto.AreaInfo;
import com.sakarrobotics.cloud.robot.registry.Robot;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.JsonNode;

/**
 * Populates {@code keenon_area_mappings} from Keenon's own live area list —
 * the one genuine gap identified by the "Keenon robot registration + area
 * sync audit": nothing in this codebase wrote to that table before this
 * slice. Reuses the already-real, already-tested {@link
 * KeenonApiClient#getAreaList(String, String)} — the exact same call
 * {@code KeenonRobotAdapter.getAreas()} makes for a live read — but here
 * the result is persisted instead of only returned.
 *
 * <p>The vendor's own live response is the only source of truth: an area
 * present in the response is upserted (never duplicated — see {@link
 * KeenonAreaMappingRepository#findByRobotIdAndKeenonAreaId}); an area
 * previously active but absent from a successful response is deactivated,
 * since Keenon's own current list is authoritative evidence it no longer
 * exists — never deactivated on a failed/exceptional call, which carries no
 * evidence at all. No area id, map id, or display name is ever invented —
 * a vendor entry with no {@code areaId} is skipped, not fabricated.
 */
@Service
@RequiredArgsConstructor
public class KeenonAreaSyncService {

    private final KeenonApiClient client;
    private final KeenonAreaMappingRepository areaMappingRepository;

    @Transactional
    public List<AreaInfo> sync(Robot robot, String storeId) {
        if (robot.getExternalRobotId() == null) {
            throw new ApiException(SakarErrorCode.INTEGRATION_UNAVAILABLE,
                    "Robot " + robot.getId() + " has no external (Keenon) identifier configured");
        }
        String robotSn = robot.getExternalRobotId();

        JsonNode response = client.getAreaList(storeId, robotSn);
        JsonNode list = response != null ? response.get("data") : null;
        List<JsonNode> vendorAreas = list != null && list.isArray()
                ? StreamSupport.stream(list.spliterator(), false).toList()
                : List.of();

        Instant now = Instant.now();
        Set<String> seenVendorAreaIds = new HashSet<>();
        List<AreaInfo> result = new ArrayList<>();

        for (JsonNode node : vendorAreas) {
            String vendorAreaId = textOrNull(node, "areaId");
            if (vendorAreaId == null) {
                // A malformed vendor entry with no id — never invent one, just skip it.
                continue;
            }
            seenVendorAreaIds.add(vendorAreaId);

            String displayName = textOrNull(node, "areaName");
            // "mapId" is not part of any live-verified response shape for this endpoint
            // today (SAKAR_LIVE_API_VALIDATION_MATRIX.md documents only areaId/areaName) —
            // read defensively in case a future/different account ever returns one, but
            // never invented: null here is the honest, already-nullable column default.
            String mapId = textOrNull(node, "mapId");

            KeenonAreaMapping mapping = areaMappingRepository.findByRobotIdAndKeenonAreaId(robot.getId(), vendorAreaId)
                    .orElseGet(KeenonAreaMapping::new);
            mapping.setOrganizationId(robot.getOrganizationId());
            mapping.setSiteId(robot.getSiteId());
            mapping.setRobotId(robot.getId());
            mapping.setKeenonStoreId(storeId);
            mapping.setKeenonMapId(mapId);
            mapping.setKeenonAreaId(vendorAreaId);
            // display_name is NOT NULL in the schema — falling back to the vendor's own area
            // id (real data, not a fabricated name) only in the edge case Keenon reports an
            // area with no name at all.
            mapping.setDisplayName(displayName != null ? displayName : vendorAreaId);
            mapping.setActive(true);
            mapping.setLastSyncedAt(now);
            KeenonAreaMapping saved = areaMappingRepository.save(mapping);

            result.add(new AreaInfo(vendorAreaId, saved.getDisplayName(), saved.getId().toString()));
        }

        // Deactivate only what THIS successful response's own absence proves gone — a
        // previously-active mapping whose vendor area id was not among today's results.
        for (KeenonAreaMapping existingMapping : areaMappingRepository.findByRobotIdAndActiveTrue(robot.getId())) {
            if (!seenVendorAreaIds.contains(existingMapping.getKeenonAreaId())) {
                existingMapping.setActive(false);
                existingMapping.setLastSyncedAt(now);
                areaMappingRepository.save(existingMapping);
            }
        }

        return result;
    }

    private static String textOrNull(JsonNode node, String field) {
        return node != null && node.hasNonNull(field) ? node.get(field).asText() : null;
    }
}
