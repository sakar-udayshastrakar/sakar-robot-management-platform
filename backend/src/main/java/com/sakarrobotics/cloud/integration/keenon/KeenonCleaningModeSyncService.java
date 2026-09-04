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
 * Populates {@code keenon_cleaning_mode_mappings} from Keenon's own live
 * cleaning-mode list — mirrors {@link KeenonAreaSyncService} exactly (same
 * upsert-not-duplicate, deactivate-only-on-evidence, never-invent-an-id
 * rules), reusing the already-real {@link KeenonApiClient#getCleaningModes}
 * call that previously had no caller anywhere in the codebase.
 *
 * <p><strong>Vendor field-name caveat:</strong> no live-captured JSON exists
 * for {@code GET .../clean/robot/strategy/clean/model} anywhere in this
 * project's evidence (SAKAR_LIVE_API_VALIDATION_MATRIX.md marks it
 * "documented, not yet live-tested" for its exact shape). {@code
 * cleanModelId} is read as the mode identifier because it is the one
 * corroborated field name for this exact concept — the sibling {@code
 * POST .../strategy/temporary/task} endpoint's own documented request body
 * uses {@code cleanModelId} to select a mode. {@code cleanModelName} is read
 * defensively as a same-vendor-convention-guess (mirrors {@code areaId}/
 * {@code areaName}); if absent, the display name falls back to the mode id
 * itself — never fabricated — exactly like {@code KeenonAreaSyncService}'s
 * {@code areaName} fallback.
 */
@Service
@RequiredArgsConstructor
public class KeenonCleaningModeSyncService {

    private final KeenonApiClient client;
    private final KeenonCleaningModeMappingRepository modeMappingRepository;

    @Transactional
    public List<KeenonCleaningModeMapping> sync(Robot robot) {
        if (robot.getExternalRobotId() == null) {
            throw new ApiException(SakarErrorCode.INTEGRATION_UNAVAILABLE,
                    "Robot " + robot.getId() + " has no external (Keenon) identifier configured");
        }
        String robotSn = robot.getExternalRobotId();

        JsonNode response = client.getCleaningModes(robotSn);
        JsonNode list = response != null ? response.get("data") : null;
        List<JsonNode> vendorModes = list != null && list.isArray()
                ? StreamSupport.stream(list.spliterator(), false).toList()
                : List.of();

        Instant now = Instant.now();
        Set<String> seenVendorModeIds = new HashSet<>();
        List<KeenonCleaningModeMapping> result = new ArrayList<>();

        for (JsonNode node : vendorModes) {
            String vendorModeId = textOrNull(node, "cleanModelId");
            if (vendorModeId == null) {
                // A malformed vendor entry with no id — never invent one, just skip it.
                continue;
            }
            seenVendorModeIds.add(vendorModeId);

            String displayName = textOrNull(node, "cleanModelName");

            KeenonCleaningModeMapping mapping = modeMappingRepository.findByRobotIdAndKeenonModeId(robot.getId(), vendorModeId)
                    .orElseGet(KeenonCleaningModeMapping::new);
            mapping.setOrganizationId(robot.getOrganizationId());
            mapping.setSiteId(robot.getSiteId());
            mapping.setRobotId(robot.getId());
            mapping.setKeenonModeId(vendorModeId);
            // display_name is NOT NULL in the schema — falling back to the vendor's own
            // mode id (real data, not a fabricated name) only if Keenon reports no name.
            mapping.setDisplayName(displayName != null ? displayName : vendorModeId);
            mapping.setActive(true);
            mapping.setLastSyncedAt(now);
            result.add(modeMappingRepository.save(mapping));
        }

        // Deactivate only what THIS successful response's own absence proves gone — a
        // previously-active mapping whose vendor mode id was not among today's results.
        for (KeenonCleaningModeMapping existingMapping : modeMappingRepository.findByRobotIdAndActiveTrue(robot.getId())) {
            if (!seenVendorModeIds.contains(existingMapping.getKeenonModeId())) {
                existingMapping.setActive(false);
                existingMapping.setLastSyncedAt(now);
                modeMappingRepository.save(existingMapping);
            }
        }

        return result;
    }

    private static String textOrNull(JsonNode node, String field) {
        return node != null && node.hasNonNull(field) ? node.get(field).asText() : null;
    }
}
