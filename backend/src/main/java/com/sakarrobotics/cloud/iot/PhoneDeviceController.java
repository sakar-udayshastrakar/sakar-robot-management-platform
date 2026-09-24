package com.sakarrobotics.cloud.iot;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sakarrobotics.cloud.common.web.ApiResponse;
import com.sakarrobotics.cloud.iot.dto.CreatePhoneDeviceRequest;
import com.sakarrobotics.cloud.security.UserPrincipal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** IoT Platform → Phone Module → Device management. */
@RestController
@RequestMapping("/api/v1/iot/phone-devices")
@RequiredArgsConstructor
@Tag(name = "IoT Platform — Phone Module")
public class PhoneDeviceController {

    private final PhoneDeviceService phoneDeviceService;

    @GetMapping
    @PreAuthorize("hasAuthority('ROBOT_VIEW')")
    @Operation(summary = "List phone devices within the caller's organization scope")
    public ApiResponse<List<PhoneDevice>> list(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(phoneDeviceService.list(principal));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ROBOT_CONFIGURE')")
    @Operation(summary = "Register a phone device (\"Device input\")")
    public ResponseEntity<ApiResponse<PhoneDevice>> register(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreatePhoneDeviceRequest request) {
        PhoneDevice device = phoneDeviceService.register(principal, request.organizationId(), request.siteId(),
                request.deviceId(), request.deviceName(), request.networkingMode());
        return ResponseEntity.status(201).body(ApiResponse.ok(device));
    }
}
