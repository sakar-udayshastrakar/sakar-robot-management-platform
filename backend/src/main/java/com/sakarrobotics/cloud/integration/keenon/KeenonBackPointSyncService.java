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
import com.sakarrobotics.cloud.robot.registry.Robot;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.JsonNode;

/**
 * Populates {@code keenon_back_point_mappings} from Keenon's own live
 * return/charging-point list — mirrors {@link KeenonCleaningModeSyncService}
 * exactly (same upsert-not-duplicate, deactivate-only-on-evidence,
 * never-invent-an-id rules), reusing the already-real {@link
 * KeenonApiClient#getBackPoints} call that, before this slice, was only
 * ever used transiently inside {@link KeenonRobotAdapter#startTask} to
 * derive a single {@code backPointId} for command dispatch — never
 * persisted into any Sakar-owned table.
 *
 * <p><strong>Vendor field-name evidence:</strong> {@code backPointId} is
 * read as the point identifier because it is already the exact field name
 * {@link KeenonRobotAdapter} itself reads from this same endpoint's
 * response today (not a guess). {@code backPointName} is read defensively
 * as a same-vendor-convention guess (mirrors {@code areaId}/{@code
 * areaName}, {@code cleanModelId}/{@code cleanModelName}); if absent, the
 * display name falls back to the point id itself — never fabricated, and
 * the observed point id {@code 39} ("1_Charging pile" per
 * SAKAR_LIVE_API_VALIDATION_MATRIX.md §9) is never hardcoded anywhere here.
 *
 * <p><strong>Response envelope:</strong> confirmed live by a raw capture —
 * {@code {data: {robotSn, backPointList: [...]}}}. {@code data} is an
 * OBJECT, not an array; the points live at {@code data.backPointList}. An
 * earlier version of this service (and {@link
 * KeenonRobotAdapter#defaultBackPointId}) read {@code response.get("data")}
 * directly as the array, which is always non-array for this endpoint's real
 * shape — silently producing zero back points regardless of what Keenon
 * actually had configured.
 */
@Service
@RequiredArgsConstructor
public class KeenonBackPointSyncService {

    private final KeenonApiClient client;
    private final KeenonBackPointMappingRepository backPointMappingRepository;

    @Transactional
    public List<KeenonBackPointMapping> sync(Robot robot) {
        if (robot.getExternalRobotId() == null) {
            throw new ApiException(SakarErrorCode.INTEGRATION_UNAVAILABLE,
                    "Robot " + robot.getId() + " has no external (Keenon) identifier configured");
        }
        String robotSn = robot.getExternalRobotId();

        // Confirmed live (raw capture): {"data":{"robotSn":..., "backPointList":[...]}} —
        // "data" is an OBJECT, the points live at data.backPointList, never a bare array
        // directly under "data".
        JsonNode response = client.getBackPoints(robotSn);
        JsonNode data = response != null ? response.get("data") : null;
        JsonNode backPointList = data != null ? data.get("backPointList") : null;
        List<JsonNode> vendorPoints = backPointList != null && backPointList.isArray()
                ? StreamSupport.stream(backPointList.spliterator(), false).toList()
                : List.of();

        Instant now = Instant.now();
        Set<String> seenVendorBackPointIds = new HashSet<>();
        List<KeenonBackPointMapping> result = new ArrayList<>();

        for (JsonNode node : vendorPoints) {
            String vendorBackPointId = textOrNull(node, "backPointId");
            if (vendorBackPointId == null) {
                // A malformed vendor entry with no id — never invent one, just skip it.
                continue;
            }
            seenVendorBackPointIds.add(vendorBackPointId);

            String displayName = textOrNull(node, "backPointName");

            KeenonBackPointMapping mapping = backPointMappingRepository
                    .findByRobotIdAndKeenonBackPointId(robot.getId(), vendorBackPointId)
                    .orElseGet(KeenonBackPointMapping::new);
            mapping.setOrganizationId(robot.getOrganizationId());
            mapping.setSiteId(robot.getSiteId());
            mapping.setRobotId(robot.getId());
            mapping.setKeenonBackPointId(vendorBackPointId);
            // display_name is NOT NULL in the schema — falling back to the vendor's own
            // point id (real data, not a fabricated name) only if Keenon reports no name.
            mapping.setDisplayName(displayName != null ? displayName : vendorBackPointId);
            mapping.setActive(true);
            mapping.setLastSyncedAt(now);
            result.add(backPointMappingRepository.save(mapping));
        }

        // Deactivate only what THIS successful response's own absence proves gone — a
        // previously-active mapping whose vendor back-point id was not among today's results.
        for (KeenonBackPointMapping existingMapping : backPointMappingRepository.findByRobotIdAndActiveTrue(robot.getId())) {
            if (!seenVendorBackPointIds.contains(existingMapping.getKeenonBackPointId())) {
                existingMapping.setActive(false);
                existingMapping.setLastSyncedAt(now);
                backPointMappingRepository.save(existingMapping);
            }
        }

        return result;
    }

    private static String textOrNull(JsonNode node, String field) {
        return node != null && node.hasNonNull(field) ? node.get(field).asText() : null;
    }
}
