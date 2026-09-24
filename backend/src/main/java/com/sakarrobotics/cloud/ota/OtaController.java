package com.sakarrobotics.cloud.ota;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sakarrobotics.cloud.common.web.ApiResponse;
import com.sakarrobotics.cloud.ota.dto.CreateSoftwareVersionRequest;
import com.sakarrobotics.cloud.ota.dto.PushSoftwareVersionRequest;
import com.sakarrobotics.cloud.security.UserPrincipal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * OTA Management → System Version Management + Update record (Robot
 * Management sidebar group's sibling; governance doc Module B, approved
 * for a real build). See {@link OtaService}'s own Javadoc for the
 * "push = recorded, never a fabricated delivery confirmation" rule.
 */
@RestController
@RequestMapping("/api/v1/ota")
@RequiredArgsConstructor
@Tag(name = "OTA Management")
public class OtaController {

    private final OtaService otaService;

    @GetMapping("/versions")
    @PreAuthorize("hasAuthority('ROBOT_VIEW')")
    @Operation(summary = "List software/firmware versions within the caller's organization scope")
    public ApiResponse<List<SoftwareVersion>> listVersions(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(otaService.listVersions(principal));
    }

    @PostMapping("/versions")
    @PreAuthorize("hasAuthority('ROBOT_CONFIGURE')")
    @Operation(summary = "Register a software/firmware version")
    public ResponseEntity<ApiResponse<SoftwareVersion>> createVersion(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateSoftwareVersionRequest request) {
        SoftwareVersion version = otaService.createVersion(principal, request.organizationId(), request.packageName(),
                request.wholeMachineSoftware(), request.packageVersion(), request.hardwareVersion(),
                request.grayscale() != null && request.grayscale(), request.sizeBytes(), request.notes());
        return ResponseEntity.status(201).body(ApiResponse.ok(version));
    }

    @PostMapping("/versions/{id}/push")
    @PreAuthorize("hasAuthority('ROBOT_CONFIGURE')")
    @Operation(summary = "Push a version to a robot — records the push; does not deliver or confirm anything on the "
            + "robot itself (no OTA delivery channel exists)")
    public ResponseEntity<ApiResponse<DeploymentRecord>> push(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody PushSoftwareVersionRequest request) {
        DeploymentRecord record = otaService.push(principal, id, request.robotId(), request.oldVersionNumber());
        return ResponseEntity.status(201).body(ApiResponse.ok(record));
    }

    @GetMapping("/deployment-records")
    @PreAuthorize("hasAuthority('ROBOT_VIEW')")
    @Operation(summary = "List deployment (push) records — \"Update record\" — newest first")
    public ApiResponse<List<DeploymentRecord>> listDeploymentRecords(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(otaService.listDeploymentRecords(principal));
    }
}
