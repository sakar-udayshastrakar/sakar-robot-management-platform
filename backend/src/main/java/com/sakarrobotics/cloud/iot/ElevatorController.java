package com.sakarrobotics.cloud.iot;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sakarrobotics.cloud.common.web.ApiResponse;
import com.sakarrobotics.cloud.iot.dto.CreateElevatorConfigurationRequest;
import com.sakarrobotics.cloud.iot.dto.CreateElevatorDeviceRequest;
import com.sakarrobotics.cloud.iot.dto.DeliverElevatorConfigurationRequest;
import com.sakarrobotics.cloud.iot.dto.UpdateElevatorConfigurationRequest;
import com.sakarrobotics.cloud.security.UserPrincipal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * IoT Platform → Elevator Module: Elevator management, Elevator
 * configuration ("set record" = the events sub-resource), and Cloud ladder
 * control configuration's "The elevator configuration is delivered" tab.
 * See {@link ElevatorService}'s own Javadoc for the real-registry vs.
 * bookkeeping-only boundary.
 */
@RestController
@RequestMapping("/api/v1/iot")
@RequiredArgsConstructor
@Tag(name = "IoT Platform — Elevator Module")
public class ElevatorController {

    private final ElevatorService elevatorService;

    @GetMapping("/elevator-devices")
    @PreAuthorize("hasAuthority('ROBOT_VIEW')")
    @Operation(summary = "List elevator devices within the caller's organization scope")
    public ApiResponse<List<ElevatorDevice>> listDevices(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(elevatorService.listDevices(principal));
    }

    @PostMapping("/elevator-devices")
    @PreAuthorize("hasAuthority('ROBOT_CONFIGURE')")
    @Operation(summary = "Register an elevator device (\"Device input\")")
    public ResponseEntity<ApiResponse<ElevatorDevice>> registerDevice(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateElevatorDeviceRequest request) {
        ElevatorDevice device = elevatorService.registerDevice(principal, request.organizationId(), request.siteId(),
                request.deviceId(), request.deviceName(), request.building(), request.protocol(),
                request.networkingMode(), request.communicationMode());
        return ResponseEntity.status(201).body(ApiResponse.ok(device));
    }

    @GetMapping("/elevator-configurations")
    @PreAuthorize("hasAuthority('ROBOT_VIEW')")
    @Operation(summary = "List elevator configurations within the caller's organization scope")
    public ApiResponse<List<ElevatorConfiguration>> listConfigurations(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(elevatorService.listConfigurations(principal));
    }

    @PostMapping("/elevator-configurations")
    @PreAuthorize("hasAuthority('ROBOT_CONFIGURE')")
    @Operation(summary = "Create an elevator configuration binding a robot to an elevator device")
    public ResponseEntity<ApiResponse<ElevatorConfiguration>> createConfiguration(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateElevatorConfigurationRequest request) {
        ElevatorConfiguration configuration = elevatorService.createConfiguration(principal, request.organizationId(),
                request.siteId(), request.elevatorDeviceId(), request.robotId(), request.name(), request.notes());
        return ResponseEntity.status(201).body(ApiResponse.ok(configuration));
    }

    @PutMapping("/elevator-configurations/{id}")
    @PreAuthorize("hasAuthority('ROBOT_CONFIGURE')")
    @Operation(summary = "Update an elevator configuration")
    public ApiResponse<ElevatorConfiguration> updateConfiguration(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateElevatorConfigurationRequest request) {
        return ApiResponse.ok(elevatorService.updateConfiguration(principal, id, request.name(), request.notes()));
    }

    @GetMapping("/elevator-configurations/{id}/events")
    @PreAuthorize("hasAuthority('ROBOT_VIEW')")
    @Operation(summary = "\"set record\" — an elevator configuration's own change history")
    public ApiResponse<List<ElevatorConfigurationEvent>> events(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        return ApiResponse.ok(elevatorService.events(principal, id));
    }

    @PostMapping("/elevator-configurations/{id}/deliver")
    @PreAuthorize("hasAuthority('ROBOT_CONFIGURE')")
    @Operation(summary = "\"The elevator configuration is delivered\" — records that an operator delivered this "
            + "configuration to a robot; never a real elevator-controller confirmation")
    public ResponseEntity<ApiResponse<ElevatorConfigurationDelivery>> deliver(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody DeliverElevatorConfigurationRequest request) {
        ElevatorConfigurationDelivery delivery = elevatorService.deliver(principal, id, request.robotId(), request.status());
        return ResponseEntity.status(201).body(ApiResponse.ok(delivery));
    }

    @GetMapping("/elevator-configuration-deliveries")
    @PreAuthorize("hasAuthority('ROBOT_VIEW')")
    @Operation(summary = "List elevator configuration delivery records — \"The elevator configuration is delivered\"")
    public ApiResponse<List<ElevatorConfigurationDelivery>> listDeliveries(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(elevatorService.listDeliveries(principal));
    }
}
