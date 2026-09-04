package com.sakarrobotics.cloud.robot.registry;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sakarrobotics.cloud.common.error.ApiException;
import com.sakarrobotics.cloud.common.error.SakarErrorCode;
import com.sakarrobotics.cloud.common.web.ApiResponse;
import com.sakarrobotics.cloud.robot.adapter.RobotAdapter;
import com.sakarrobotics.cloud.robot.adapter.RobotAdapterRegistry;
import com.sakarrobotics.cloud.robot.adapter.dto.AreaInfo;
import com.sakarrobotics.cloud.robot.adapter.dto.BatteryInfo;
import com.sakarrobotics.cloud.robot.adapter.dto.RobotStatusSnapshot;
import com.sakarrobotics.cloud.robot.registry.dto.RegisterRobotRequest;
import com.sakarrobotics.cloud.robot.registry.dto.RobotMqttCredentialResponse;
import com.sakarrobotics.cloud.robot.registry.dto.RobotResponse;
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

    @GetMapping
    @PreAuthorize("hasAuthority('ROBOT_VIEW')")
    @Operation(summary = "List robots within the caller's organization scope")
    public ApiResponse<Page<RobotResponse>> list(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int pageSize) {
        Page<RobotResponse> result = robotService.listAccessible(principal, page, pageSize)
                .map(robot -> RobotResponse.from(robot, robotCapabilityService.supportedCapabilities(robot.getRobotModelId())));
        return ApiResponse.ok(result);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ROBOT_VIEW')")
    @Operation(summary = "Get a single robot's registry detail")
    public ApiResponse<RobotResponse> get(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        Robot robot = robotService.getAccessibleOrThrow(principal, id);
        return ApiResponse.ok(RobotResponse.from(robot, robotCapabilityService.supportedCapabilities(robot.getRobotModelId())));
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
        RobotResponse response = RobotResponse.from(guardedRobot, robotCapabilityService.supportedCapabilities(guardedRobot.getRobotModelId()));
        return ResponseEntity.status(201).body(ApiResponse.ok(response));
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasAuthority('ROBOT_CONFIGURE')")
    @Operation(summary = "Activate a registered robot")
    public ApiResponse<RobotResponse> activate(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        Robot robot = robotService.activate(principal, id);
        return ApiResponse.ok(RobotResponse.from(robot, robotCapabilityService.supportedCapabilities(robot.getRobotModelId())));
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasAuthority('ROBOT_CONFIGURE')")
    @Operation(summary = "Deactivate a robot")
    public ApiResponse<RobotResponse> deactivate(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        Robot robot = robotService.deactivate(principal, id);
        return ApiResponse.ok(RobotResponse.from(robot, robotCapabilityService.supportedCapabilities(robot.getRobotModelId())));
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
}
