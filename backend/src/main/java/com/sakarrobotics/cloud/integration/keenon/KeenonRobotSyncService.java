package com.sakarrobotics.cloud.integration.keenon;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.sakarrobotics.cloud.common.error.ApiException;
import com.sakarrobotics.cloud.common.error.SakarErrorCode;
import com.sakarrobotics.cloud.integration.keenon.dto.KeenonRobotSyncResult;
import com.sakarrobotics.cloud.org.Organization;
import com.sakarrobotics.cloud.org.OrganizationService;
import com.sakarrobotics.cloud.org.OrganizationType;
import com.sakarrobotics.cloud.robot.registry.AdapterType;
import com.sakarrobotics.cloud.robot.registry.IntegrationPath;
import com.sakarrobotics.cloud.robot.registry.Robot;
import com.sakarrobotics.cloud.robot.registry.RobotManufacturer;
import com.sakarrobotics.cloud.robot.registry.RobotManufacturerRepository;
import com.sakarrobotics.cloud.robot.registry.RobotModel;
import com.sakarrobotics.cloud.robot.registry.RobotModelRepository;
import com.sakarrobotics.cloud.robot.registry.RobotRepository;
import com.sakarrobotics.cloud.robot.registry.RobotService;
import com.sakarrobotics.cloud.robot.registry.SakarSerialNumberService;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.JsonNode;

/**
 * Discovers every robot the configured Keenon Cloud account can see for a
 * given store and idempotently mirrors it into Sakar's own {@code robots} /
 * {@code robot_models} registry — the robot-discovery slice referenced by
 * Master Requirements §6.A "Robot Management" ("identity: serial/mftCode —
 * CONFIRMED available from Keenon Cloud's robot list today"). That vendor
 * evidence is used to populate {@code external_robot_id}/{@code
 * vendorSerialNumber} below — the Sakar-facing {@code serial_number} is
 * Sakar's own generated identity, never Keenon's {@code mftCode}.
 *
 * <p><strong>Response envelope:</strong> confirmed live by a raw capture —
 * {@code {code, msg, data: [...]}}; {@code data} is a flat ARRAY of robot
 * records (unlike area-list/back-point, where {@code data} is an object and
 * the real list is nested one level deeper) — verified from the real
 * response, never assumed from either sibling endpoint's shape.
 *
 * <p><strong>Stable identity:</strong> the vendor's own {@code robotId}
 * (e.g. {@code 94:BA:06:CA:99:F3}) is stored as {@link
 * Robot#getExternalRobotId()} — the same field {@link KeenonRobotAdapter}
 * already reads as {@code robotSn} for every other Keenon call. {@link
 * Robot#getSerialNumber()} is Sakar's OWN generated identity ({@code
 * SR-<PREFIX>-YYYY-NNNNNN}, prefix per robot-model family — see {@link
 * SakarSerialNumberService}) — it is never derived from any Keenon field. A
 * Keenon model with no configured Sakar prefix fails registration with
 * {@code UNSUPPORTED_ROBOT_MODEL_SERIAL_PREFIX} rather than inventing one.
 * {@code mftCode} (a genuine vendor manufacturer serial, e.g. {@code
 * QC402602X00002}) is preserved separately as {@link
 * Robot#getVendorSerialNumber()}; it must never overwrite the Sakar serial,
 * and its absence never blocks registration.
 *
 * <p><strong>Tenant scoping:</strong> every discovered robot is created
 * under the caller-supplied, access-checked {@code organizationId}. This
 * service also fails the whole sync fast if that organization is not
 * {@link OrganizationType#SAKAR_ROOT} — {@link RobotService#register}
 * itself independently enforces the identical rule per-robot, but a single
 * upfront check gives one clear error instead of the same rejection
 * repeated once per discovered robot.
 *
 * <p><strong>What is deliberately NOT touched:</strong> {@code
 * onlineStatus}/{@code power}/{@code appVersion}/{@code city} are live
 * telemetry, not registry identity — no column was added for them
 * (discovery + upsert only). An already-registered robot's Sakar-chosen
 * {@code name}/{@code siteId}/lifecycle {@code status}/Sakar {@code
 * serialNumber} are never overwritten by a re-sync — only {@code
 * robotModelId} and {@code vendorSerialNumber} are refreshed, and only if
 * Keenon now reports a different value for either.
 */
@Service
@RequiredArgsConstructor
public class KeenonRobotSyncService {

    private static final Logger log = LoggerFactory.getLogger(KeenonRobotSyncService.class);
    private static final String KEENON_MANUFACTURER_NAME = "Keenon";

    private final KeenonApiClient client;
    private final RobotRepository robotRepository;
    private final RobotModelRepository robotModelRepository;
    private final RobotManufacturerRepository robotManufacturerRepository;
    private final RobotService robotService;
    private final OrganizationService organizationService;
    private final SakarSerialNumberService sakarSerialNumberService;

    public KeenonRobotSyncResult sync(UUID organizationId, String storeId) {
        Organization organization = organizationService.getOrThrow(organizationId);
        if (organization.getOrgType() != OrganizationType.SAKAR_ROOT) {
            throw new ApiException(SakarErrorCode.EXTERNAL_ROBOT_ID_NOT_ALLOWED,
                    "Keenon robot discovery may only sync into a Sakar Robotics (SAKAR_ROOT) organization");
        }

        JsonNode response = client.getRobotList(storeId);
        List<JsonNode> vendorRobots = vendorRobotsOf(response);

        int created = 0;
        int updated = 0;
        int unchanged = 0;
        List<KeenonRobotSyncResult.Failure> failures = new ArrayList<>();

        for (JsonNode node : vendorRobots) {
            String vendorRobotId = textOrNull(node, "robotId");
            if (vendorRobotId == null || vendorRobotId.isBlank()) {
                // Never invent an identity for a vendor entry that carries none.
                failures.add(new KeenonRobotSyncResult.Failure(null, "Vendor record has no robotId"));
                continue;
            }
            try {
                switch (upsert(organizationId, vendorRobotId, node)) {
                    case CREATED -> created++;
                    case UPDATED -> updated++;
                    case UNCHANGED -> unchanged++;
                }
            } catch (ApiException ex) {
                log.warn("Keenon robot sync: sync failed for vendor robot {}: {}", vendorRobotId, ex.getMessage());
                failures.add(new KeenonRobotSyncResult.Failure(vendorRobotId, ex.getMessage()));
            } catch (Exception ex) {
                // Defensive: one malformed/unexpected robot record must never abort the batch.
                log.warn("Keenon robot sync: unexpected failure syncing vendor robot {}: {}", vendorRobotId, ex.getMessage());
                failures.add(new KeenonRobotSyncResult.Failure(vendorRobotId, "Unexpected error"));
            }
        }

        return new KeenonRobotSyncResult(vendorRobots.size(), created, updated, unchanged, failures.size(), failures);
    }

    private enum Outcome { CREATED, UPDATED, UNCHANGED }

    private Outcome upsert(UUID organizationId, String vendorRobotId, JsonNode node) {
        String vendorModelName = textOrNull(node, "robotModel");
        String mftCode = textOrNull(node, "mftCode");
        String robotName = textOrNull(node, "robotName");

        Optional<Robot> existing = robotRepository.findByExternalRobotId(vendorRobotId);
        if (existing.isPresent()) {
            Robot robot = existing.get();
            boolean changed = false;
            if (vendorModelName != null && !vendorModelName.isBlank()) {
                RobotModel resolvedModel = resolveModel(vendorModelName);
                if (!resolvedModel.getId().equals(robot.getRobotModelId())) {
                    robot.setRobotModelId(resolvedModel.getId());
                    changed = true;
                }
            }
            // vendor_serial_number is vendor-mirrored reference data (like robotModelId), never
            // the Sakar identity — refreshing it here never touches robot.serialNumber.
            if (mftCode != null && !mftCode.isBlank() && !mftCode.equals(robot.getVendorSerialNumber())) {
                robot.setVendorSerialNumber(mftCode);
                changed = true;
            }
            if (changed) {
                robotRepository.save(robot);
                return Outcome.UPDATED;
            }
            return Outcome.UNCHANGED;
        }

        // mftCode is preserved as vendorSerialNumber when present, but its absence never
        // blocks registration — the Sakar serial_number below is generated independently of it.
        // nextSerialNumber(model) throws UNSUPPORTED_ROBOT_MODEL_SERIAL_PREFIX for a model with
        // no configured prefix — this robot then fails (caught by the per-robot try/catch in
        // sync()) rather than ever inventing a prefix or falling back to any default.
        RobotModel resolvedModel = resolveModel(vendorModelName);
        String sakarSerialNumber = sakarSerialNumberService.nextSerialNumber(resolvedModel);
        String name = (robotName != null && !robotName.isBlank()) ? robotName : vendorRobotId;
        robotService.register(organizationId, null, resolvedModel.getId(), name, sakarSerialNumber, vendorRobotId, mftCode);
        return Outcome.CREATED;
    }

    private RobotModel resolveModel(String vendorModelName) {
        if (vendorModelName == null || vendorModelName.isBlank()) {
            throw new ApiException(SakarErrorCode.VALIDATION_FAILED, "Vendor robot record has no robotModel");
        }
        RobotManufacturer manufacturer = robotManufacturerRepository.findByNameIgnoreCase(KEENON_MANUFACTURER_NAME)
                .orElseGet(() -> robotManufacturerRepository.save(new RobotManufacturer(KEENON_MANUFACTURER_NAME)));
        return robotModelRepository.findByManufacturerIdAndName(manufacturer.getId(), vendorModelName)
                .orElseGet(() -> {
                    // A brand-new Keenon model has no configured Sakar serial prefix yet — left
                    // null here deliberately; see nextSerialNumber(RobotModel)'s own fail-safe.
                    RobotModel model = new RobotModel();
                    model.setManufacturerId(manufacturer.getId());
                    model.setName(vendorModelName);
                    model.setAdapterType(AdapterType.KEENON_CLOUD);
                    model.setIntegrationPath(IntegrationPath.KEENON_CLOUD_DEPENDENT);
                    return robotModelRepository.save(model);
                });
    }

    private static List<JsonNode> vendorRobotsOf(JsonNode response) {
        JsonNode data = response != null ? response.get("data") : null;
        if (data == null || !data.isArray()) {
            return List.of();
        }
        List<JsonNode> result = new ArrayList<>();
        data.forEach(result::add);
        return result;
    }

    private static String textOrNull(JsonNode node, String field) {
        return node != null && node.hasNonNull(field) ? node.get(field).asText() : null;
    }
}
