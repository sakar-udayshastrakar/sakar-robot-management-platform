package com.sakarrobotics.cloud.integration.keenon;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import com.sakarrobotics.cloud.common.error.ApiException;
import com.sakarrobotics.cloud.common.error.SakarErrorCode;
import com.sakarrobotics.cloud.robot.adapter.RobotAdapter;
import com.sakarrobotics.cloud.robot.adapter.dto.AdapterOperationResult;
import com.sakarrobotics.cloud.robot.adapter.dto.AdapterTaskRequest;
import com.sakarrobotics.cloud.robot.adapter.dto.AreaInfo;
import com.sakarrobotics.cloud.robot.adapter.dto.BatteryInfo;
import com.sakarrobotics.cloud.robot.adapter.dto.MapInfo;
import com.sakarrobotics.cloud.robot.adapter.dto.RobotStatusSnapshot;
import com.sakarrobotics.cloud.robot.adapter.dto.TelemetryReading;
import com.sakarrobotics.cloud.robot.registry.AdapterType;
import com.sakarrobotics.cloud.robot.registry.Robot;
import com.sakarrobotics.cloud.robot.registry.RobotCapabilityType;

import lombok.RequiredArgsConstructor;

/**
 * The current, live-tested, KEENON-CLOUD DEPENDENT integration
 * (Master Requirements Part 10/40). Every read/write method here maps 1:1
 * to an endpoint in {@code SAKAR_LIVE_API_VALIDATION_MATRIX.md}.
 *
 * <p><strong>Scope note:</strong> {@code resumeTask} has no corresponding
 * Keenon Open Platform endpoint in the supplied evidence — it throws
 * {@code UNSUPPORTED_CAPABILITY} rather than a generic error, since this is
 * a vendor-surface limitation, not a bug. {@code lock}/{@code unlock} are
 * intentionally absent from this adapter entirely: Keenon Cloud does not
 * expose motor control at all (Master Requirements Part 10), and even the
 * Peanut-SDK-based local path remains {@code REQUIRES PHYSICAL C40 TEST}
 * (Part 11) — no adapter in this codebase implements lock/unlock.
 */
@Component
@RequiredArgsConstructor
public class KeenonRobotAdapter implements RobotAdapter {

    private static final Set<RobotCapabilityType> SUPPORTED = Set.of(
            RobotCapabilityType.GET_STATUS,
            RobotCapabilityType.GET_BATTERY,
            RobotCapabilityType.GET_TELEMETRY,
            RobotCapabilityType.GET_AREAS,
            RobotCapabilityType.GET_MAP,
            RobotCapabilityType.START_TASK,
            RobotCapabilityType.STOP_TASK,
            RobotCapabilityType.PAUSE_TASK,
            RobotCapabilityType.RETURN_TO_DOCK,
            RobotCapabilityType.CLEANING);

    private final KeenonApiClient client;
    private final KeenonAreaMappingRepository areaMappingRepository;

    @Override
    public AdapterType adapterType() {
        return AdapterType.KEENON_CLOUD;
    }

    @Override
    public Set<RobotCapabilityType> supportedCapabilities() {
        return SUPPORTED;
    }

    @Override
    public RobotStatusSnapshot getStatus(Robot robot) {
        JsonNode response = client.getRobotStatus(externalId(robot));
        JsonNode data = dataOf(response);
        return new RobotStatusSnapshot(
                textOrNull(data, "mainState"),
                textOrNull(data, "subState"),
                data != null,
                Instant.now(),
                response);
    }

    @Override
    public BatteryInfo getBattery(Robot robot) {
        JsonNode response = client.getBatteryLevel(externalId(robot));
        JsonNode data = dataOf(response);
        int percentage = data != null && data.has("batteryLevel") ? data.get("batteryLevel").asInt() : -1;
        return new BatteryInfo(percentage, false, Instant.now());
    }

    @Override
    public List<TelemetryReading> getTelemetry(Robot robot) {
        JsonNode response = client.getCleaningStatus(externalId(robot));
        return List.of(new TelemetryReading("cleaning_status_raw", response != null ? response.toString() : null, null, Instant.now()));
    }

    @Override
    public List<AreaInfo> getAreas(Robot robot) {
        List<KeenonAreaMapping> mappings = areaMappingRepository.findByRobotIdAndActiveTrue(robot.getId());
        String storeId = mappings.stream()
                .findFirst()
                .map(KeenonAreaMapping::getKeenonStoreId)
                .orElseThrow(() -> new ApiException(SakarErrorCode.RESOURCE_NOT_FOUND,
                        "No synced Keenon store mapping for robot " + robot.getId()));
        // Correlates each LIVE vendor area (by its current keenonAreaId) back to the Sakar
        // mapping row a caller needs for START_TASK — a vendor area with no synced row yet
        // simply has no entry here, never a fabricated one. first-wins on a duplicate
        // keenonAreaId rather than assuming the data is always perfectly deduplicated.
        Map<String, String> sakarAreaIdByVendorAreaId = mappings.stream()
                .filter(m -> m.getKeenonAreaId() != null)
                .collect(Collectors.toMap(KeenonAreaMapping::getKeenonAreaId, m -> m.getId().toString(), (a, b) -> a));

        // Response envelope: see KeenonAreaListParser for the confirmed-live, raw-captured
        // shape (data.entities[], each entity a per-map/floor group of two parallel arrays).
        JsonNode response = client.getAreaList(storeId, externalId(robot));
        return KeenonAreaListParser.flatten(response).stream()
                .map(vendorArea -> new AreaInfo(vendorArea.areaId(), vendorArea.areaName(),
                        sakarAreaIdByVendorAreaId.get(vendorArea.areaId())))
                .toList();
    }

    @Override
    public MapInfo getMap(Robot robot) {
        // Not exercised in the live evidence in Part 40 — map imagery was confirmed
        // via a different Keenon endpoint in prior audits, not in this adapter's
        // tested surface. Fail closed rather than guessing a response shape.
        throw new ApiException(SakarErrorCode.FEATURE_NOT_YET_IMPLEMENTED,
                "Map retrieval via the Keenon adapter is not yet wired in this phase");
    }

    @Override
    public AdapterOperationResult startTask(Robot robot, AdapterTaskRequest request) {
        if (request.sakarAreaIds() == null || request.sakarAreaIds().isEmpty()) {
            return AdapterOperationResult.rejected("At least one area must be specified");
        }
        List<String> vendorAreaIds = request.sakarAreaIds().stream()
                .map(id -> areaMappingRepository.findByIdAndActiveTrue(java.util.UUID.fromString(id))
                        .orElseThrow(() -> new ApiException(SakarErrorCode.RESOURCE_NOT_FOUND,
                                "No active Keenon area mapping for Sakar area id " + id))
                        .getKeenonAreaId())
                .toList();

        Map<String, Object> body = Map.of(
                "robotSn", externalId(robot),
                "areaIdList", vendorAreaIds,
                "cleanModelId", cleanModelIdFor(request.mode()),
                "cleanTimes", Math.max(1, request.repeatCount()),
                "backPointId", defaultBackPointId(robot));

        JsonNode response = client.postTemporaryTask(body);
        return toOperationResult(response);
    }

    @Override
    public AdapterOperationResult stopTask(Robot robot) {
        return toOperationResult(client.postFinishTask(Map.of("robotSn", externalId(robot))));
    }

    @Override
    public AdapterOperationResult pauseTask(Robot robot) {
        return toOperationResult(client.postPauseTask(Map.of("robotSn", externalId(robot))));
    }

    @Override
    public AdapterOperationResult resumeTask(Robot robot) {
        throw new ApiException(SakarErrorCode.UNSUPPORTED_CAPABILITY,
                "Keenon Open Platform does not expose a resume-task API (Master Requirements Part 40)");
    }

    @Override
    public AdapterOperationResult returnToDock(Robot robot) {
        return toOperationResult(client.postRechargeTask(Map.of("robotSn", externalId(robot))));
    }

    private static AdapterOperationResult toOperationResult(JsonNode response) {
        if (response == null) {
            // The HTTP call itself completed without throwing — the vendor was genuinely
            // contacted, it just returned nothing usable. Distinct from a pre-vendor-call
            // rejection (see AdapterOperationResult#rejected's Javadoc).
            return AdapterOperationResult.rejectedByVendor("Empty response from Keenon Open Platform");
        }
        String code = textOrNull(response, "code");
        // 610000 is Keenon's own "accepted" receipt code (Part 40) — this is deliberately the
        // ONLY place in the codebase that reads a raw Keenon status code; everywhere else sees
        // only AdapterOperationResult.accepted()/physicallyConfirmed(), never "610000" itself.
        boolean accepted = "610000".equals(code);
        return accepted
                ? AdapterOperationResult.accepted(code, "Accepted by Keenon Open Platform; not yet physically confirmed")
                : AdapterOperationResult.rejectedByVendor("Keenon Open Platform returned code " + code);
    }

    private String externalId(Robot robot) {
        if (robot.getExternalRobotId() == null) {
            throw new ApiException(SakarErrorCode.INTEGRATION_UNAVAILABLE,
                    "Robot " + robot.getId() + " has no external (Keenon) identifier configured");
        }
        return robot.getExternalRobotId();
    }

    private String defaultBackPointId(Robot robot) {
        // Confirmed live (raw capture): {"data":{"robotSn":..., "backPointList":[...]}} —
        // "data" is an OBJECT, the points live at data.backPointList, never a bare array
        // directly under "data" (that earlier assumption never actually matched Keenon's
        // real shape for this endpoint, which is why this robot's real, live-configured
        // charging point was never found).
        JsonNode response = client.getBackPoints(externalId(robot));
        JsonNode data = dataOf(response);
        JsonNode backPointList = data != null ? data.get("backPointList") : null;
        if (backPointList != null && backPointList.isArray() && !backPointList.isEmpty()) {
            return textOrNull(backPointList.get(0), "backPointId");
        }
        throw new ApiException(SakarErrorCode.RESOURCE_NOT_FOUND, "No return/charging point configured for this robot");
    }

    /** Sakar's generic {@code mode} (e.g. "SWEEP") -> Keenon {@code cleanModelId}. Never expose the raw vendor id publicly (Master Requirements "Cleaning"). */
    private static int cleanModelIdFor(String sakarMode) {
        return switch (sakarMode == null ? "" : sakarMode.toUpperCase()) {
            case "SWEEP_MOP" -> 101;
            case "WATER_SUCTION" -> 102;
            case "SWEEP_VACUUM" -> 103;
            case "SWEEP_PUSH" -> 104;
            case "SWEEP" -> 105;
            default -> throw new ApiException(SakarErrorCode.VALIDATION_FAILED, "Unknown cleaning mode: " + sakarMode);
        };
    }

    private static JsonNode dataOf(JsonNode response) {
        return response != null ? response.get("data") : null;
    }

    private static String textOrNull(JsonNode node, String field) {
        return node != null && node.hasNonNull(field) ? node.get(field).asText() : null;
    }
}
