package com.sakarrobotics.cloud.robot.registry;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sakarrobotics.cloud.common.error.ApiException;
import com.sakarrobotics.cloud.common.error.SakarErrorCode;
import com.sakarrobotics.cloud.common.web.ApiResponse;
import com.sakarrobotics.cloud.map.RobotMap;
import com.sakarrobotics.cloud.map.RobotMapImageService;
import com.sakarrobotics.cloud.map.RobotMapRepository;
import com.sakarrobotics.cloud.map.RobotMapResponse;
import com.sakarrobotics.cloud.robot.adapter.RobotAdapter;
import com.sakarrobotics.cloud.robot.adapter.RobotAdapterRegistry;
import com.sakarrobotics.cloud.robot.adapter.dto.AreaInfo;
import com.sakarrobotics.cloud.robot.adapter.dto.BatteryInfo;
import com.sakarrobotics.cloud.robot.adapter.dto.RobotStatusSnapshot;
import com.sakarrobotics.cloud.robot.registry.dto.RegisterRobotRequest;
import com.sakarrobotics.cloud.robot.registry.dto.RobotMqttCredentialResponse;
import com.sakarrobotics.cloud.robot.registry.dto.RobotResponse;
import com.sakarrobotics.cloud.telemetry.RobotConnectivityService;
import com.sakarrobotics.cloud.telemetry.RobotStatus;
import com.sakarrobotics.cloud.security.UserPrincipal;
import com.sakarrobotics.cloud.security.access.TenantAccessGuard;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Vendor-neutral robot registry API (SAKAR_ROBOT_PLATFORM_API_SPEC.md §1.4).
 * No response here ever contains a Keenon-specific field, id, or status
 * code — see {@link RobotResponse}.
 */
@RestController
@RequestMapping("/api/v1/robots")
@RequiredArgsConstructor
@Tag(name = "Robots")
public class RobotController {

    private final RobotService robotService;
    private final RobotModelRepository robotModelRepository;
    private final RobotCapabilityService robotCapabilityService;
    private final RobotAdapterRegistry robotAdapterRegistry;
    private final RobotCredentialService robotCredentialService;
    private final TenantAccessGuard tenantAccessGuard;
    private final RobotMapRepository robotMapRepository;
    private final RobotMapImageService robotMapImageService;
    private final RobotConnectivityService robotConnectivityService;

    @GetMapping
    @PreAuthorize("hasAuthority('ROBOT_VIEW')")
    @Operation(summary = "List robots within the caller's organization scope")
    public ApiResponse<Page<RobotResponse>> list(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int pageSize) {
        Page<Robot> robots = robotService.listAccessible(principal, page, pageSize);
        // One robot_status query for the whole page, not one per row.
        Map<UUID, RobotStatus> statuses = robotConnectivityService.rowsFor(
                robots.getContent().stream().map(Robot::getId).toList());
        Page<RobotResponse> result = robots.map(robot -> {
            RobotStatus status = statuses.get(robot.getId());
            return RobotResponse.from(robot, robotCapabilityService.supportedCapabilities(robot.getRobotModelId()),
                    robotConnectivityService.evaluate(status), status);
        });
        return ApiResponse.ok(result);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ROBOT_VIEW')")
    @Operation(summary = "Get a single robot's registry detail")
    public ApiResponse<RobotResponse> get(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        Robot robot = robotService.getAccessibleOrThrow(principal, id);
        return ApiResponse.ok(respond(robot));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ROBOT_CONFIGURE')")
    @Operation(summary = "Register a new robot (Robot Registry foundation)")
    public ResponseEntity<ApiResponse<RobotResponse>> register(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody RegisterRobotRequest request) {
        // A configuring user may only register a robot into an organization they can reach —
        // enforced here, the same way SiteController.create() gates its own organization-scoped
        // write (registration audit finding: this was previously an unenforced comment only).
        tenantAccessGuard.assertOrganizationAccess(principal, request.organizationId());
        var guardedRobot = robotService.register(request.organizationId(), request.siteId(), request.robotModelId(),
                request.name(), request.serialNumber(), request.externalRobotId());
        RobotResponse response = respond(guardedRobot);
        return ResponseEntity.status(201).body(ApiResponse.ok(response));
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasAuthority('ROBOT_CONFIGURE')")
    @Operation(summary = "Activate a registered robot")
    public ApiResponse<RobotResponse> activate(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        Robot robot = robotService.activate(principal, id);
        return ApiResponse.ok(respond(robot));
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasAuthority('ROBOT_CONFIGURE')")
    @Operation(summary = "Deactivate a robot")
    public ApiResponse<RobotResponse> deactivate(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        Robot robot = robotService.deactivate(principal, id);
        return ApiResponse.ok(respond(robot));
    }

    @GetMapping("/{id}/status")
    @PreAuthorize("hasAuthority('ROBOT_VIEW')")
    @Operation(summary = "Read a robot's current status through its configured adapter "
            + "(returns UNSUPPORTED_CAPABILITY if this robot model does not support GET_STATUS)")
    public ApiResponse<RobotStatusSnapshot> status(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        Robot robot = robotService.getAccessibleOrThrow(principal, id);
        robotCapabilityService.assertSupported(robot.getRobotModelId(), RobotCapabilityType.GET_STATUS);
        RobotAdapter adapter = adapterFor(robot);
        return ApiResponse.ok(adapter.getStatus(robot));
    }

    @GetMapping("/{id}/battery")
    @PreAuthorize("hasAuthority('ROBOT_VIEW')")
    @Operation(summary = "Read a robot's current battery level through its configured adapter "
            + "(returns UNSUPPORTED_CAPABILITY if this robot model does not support GET_BATTERY) — "
            + "same real adapter call RobotAdapter.getBattery() already implements for KEENON_CLOUD, "
            + "previously wired into no REST endpoint at all")
    public ApiResponse<BatteryInfo> battery(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        Robot robot = robotService.getAccessibleOrThrow(principal, id);
        robotCapabilityService.assertSupported(robot.getRobotModelId(), RobotCapabilityType.GET_BATTERY);
        RobotAdapter adapter = adapterFor(robot);
        return ApiResponse.ok(adapter.getBattery(robot));
    }

    @GetMapping("/{id}/areas")
    @PreAuthorize("hasAuthority('ROBOT_VIEW')")
    @Operation(summary = "List a robot's cleanable areas/zones through its configured adapter "
            + "(returns UNSUPPORTED_CAPABILITY if this robot model does not support GET_AREAS) — "
            + "metadata only (vendor area id + display name + the Sakar-side mapping id needed to "
            + "issue START_TASK, when synced), no polygon geometry: the adapter does not receive "
            + "or expose any, so none is fabricated here")
    public ApiResponse<List<AreaInfo>> areas(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        Robot robot = robotService.getAccessibleOrThrow(principal, id);
        robotCapabilityService.assertSupported(robot.getRobotModelId(), RobotCapabilityType.GET_AREAS);
        RobotAdapter adapter = adapterFor(robot);
        return ApiResponse.ok(adapter.getAreas(robot));
    }

    @GetMapping("/{id}/map")
    @PreAuthorize("hasAuthority('ROBOT_VIEW')")
    @Operation(summary = "Get a robot's synced map metadata (Raw Keenon PNG Map Storage + Sync slice) — "
            + "vendorMapId/name/width/height/mapMd5/updatedAt only, never a filesystem path, vendor "
            + "credentials, or a raw Keenon payload. Deliberately NOT gated on the GET_MAP capability: "
            + "a previously-synced map must stay servable even for a model (e.g. C40 S) whose GET_MAP "
            + "capability row is false — see KeenonMapImageSyncScheduler's own Javadoc for why that "
            + "capability is unreliable. Returns RESOURCE_NOT_FOUND if no map has been synced yet.")
    public ApiResponse<RobotMapResponse> map(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        Robot robot = robotService.getAccessibleOrThrow(principal, id);
        RobotMap robotMap = mapOrThrow(robot.getId());
        return ApiResponse.ok(RobotMapResponse.from(robotMap));
    }

    @GetMapping(value = "/{id}/map/image", produces = MediaType.IMAGE_PNG_VALUE)
    @PreAuthorize("hasAuthority('ROBOT_VIEW')")
    @Operation(summary = "Get a robot's synced map as a raw PNG (Raw Keenon PNG Map Storage + Sync "
            + "slice) — serves only the already-validated file RobotMapImageService reads from local "
            + "storage, never a live Keenon call. ETag is derived from the vendor's own mapMd5; a "
            + "matching If-None-Match returns 304. Returns RESOURCE_NOT_FOUND (never a path, a stack "
            + "trace, or a 500) whether no map was ever synced, the file is missing, or it is empty/"
            + "corrupt on disk.")
    public ResponseEntity<byte[]> mapImage(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id,
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        Robot robot = robotService.getAccessibleOrThrow(principal, id);
        RobotMap robotMap = mapOrThrow(robot.getId());
        // Set as a literal header rather than via CacheControl.cachePrivate().mustRevalidate() —
        // that builder always emits "must-revalidate, private" regardless of call order (a fixed
        // internal directive sequence), whereas this tenant-scoped image API's contract is
        // specifically "private, must-revalidate".
        String cacheControl = "private, must-revalidate";
        String etag = robotMapImageService.etagFor(robotMap);

        if (etag != null && etag.equals(ifNoneMatch)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).header(HttpHeaders.CACHE_CONTROL, cacheControl).eTag(etag).build();
        }
        // Path is resolved entirely inside RobotMapImageService from robot.getId()/robotMap.getId()
        // (both server-side UUIDs) — never from robotMap.getImageUrl() and never from any request
        // parameter, since none exists on this endpoint.
        byte[] bytes = robotMapImageService.readImageBytes(robot.getId(), robotMap.getId());
        ResponseEntity.BodyBuilder response = ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, cacheControl)
                .contentType(MediaType.IMAGE_PNG);
        if (etag != null) {
            response.eTag(etag);
        }
        return response.body(bytes);
    }

    private RobotMap mapOrThrow(UUID robotId) {
        return robotMapRepository.findByRobotId(robotId)
                .orElseThrow(() -> new ApiException(SakarErrorCode.RESOURCE_NOT_FOUND, "No map synced for robot " + robotId));
    }

    @PostMapping("/{id}/mqtt-credentials")
    @PreAuthorize("hasAuthority('ROBOT_CONFIGURE')")
    @Operation(summary = "Provision or rotate this robot's MQTT credential (Phase 3) — "
            + "the raw secret is returned once here and never stored or retrievable in plaintext again")
    public ApiResponse<RobotMqttCredentialResponse> provisionMqttCredentials(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        robotService.getAccessibleOrThrow(principal, id); // tenant check before issuing any credential
        return ApiResponse.ok(robotCredentialService.provisionOrRotate(principal, id));
    }

    @DeleteMapping("/{id}/mqtt-credentials")
    @PreAuthorize("hasAuthority('ROBOT_CONFIGURE')")
    @Operation(summary = "Revoke this robot's MQTT credential (Phase 3 Security Hardening) — "
            + "deletes the stored hash; see RobotCredentialService#revoke for what this does and does not enforce today")
    public ApiResponse<Void> revokeMqttCredentials(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        robotService.getAccessibleOrThrow(principal, id); // tenant check before revoking any credential
        robotCredentialService.revoke(principal, id);
        return ApiResponse.ok(null);
    }

    private RobotAdapter adapterFor(Robot robot) {
        RobotModel model = robotModelRepository.findById(robot.getRobotModelId())
                .orElseThrow(() -> new ApiException(SakarErrorCode.ROBOT_MODEL_NOT_FOUND, "Robot model not found"));
        return robotAdapterRegistry.resolve(model.getAdapterType());
    }

    /**
     * Single-robot response builder — every non-list endpoint goes through here so a
     * robot's connectionStatus is produced by exactly one calculation
     * ({@link RobotConnectivityService}), identical to the one the list endpoint and
     * the offline-alert sweep use.
     */
    private RobotResponse respond(Robot robot) {
        RobotStatus status = robotConnectivityService.rowFor(robot.getId());
        return RobotResponse.from(robot, robotCapabilityService.supportedCapabilities(robot.getRobotModelId()),
                robotConnectivityService.evaluate(status), status);
    }
}
