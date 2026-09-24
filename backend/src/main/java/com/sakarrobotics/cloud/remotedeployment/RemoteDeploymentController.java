package com.sakarrobotics.cloud.remotedeployment;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sakarrobotics.cloud.common.web.ApiResponse;
import com.sakarrobotics.cloud.remotedeployment.dto.CreateRemoteDeploymentRequest;
import com.sakarrobotics.cloud.remotedeployment.dto.UpdateRemoteDeploymentStatusRequest;
import com.sakarrobotics.cloud.security.UserPrincipal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Operation And Maintenance Platform → Remote Deployment (bookkeeping only — see {@link RemoteDeploymentStatus}). */
@RestController
@RequestMapping("/api/v1/remote-deployments")
@RequiredArgsConstructor
@Tag(name = "Remote Deployment")
public class RemoteDeploymentController {

    private final RemoteDeploymentService remoteDeploymentService;

    @GetMapping
    @PreAuthorize("hasAuthority('ROBOT_VIEW')")
    @Operation(summary = "List remote deployment records within the caller's organization scope")
    public ApiResponse<List<RemoteDeploymentRecord>> list(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(remoteDeploymentService.listAccessible(principal));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ROBOT_CONFIGURE')")
    @Operation(summary = "Record that a configuration deployment to a robot was started")
    public ResponseEntity<ApiResponse<RemoteDeploymentRecord>> create(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateRemoteDeploymentRequest request) {
        RemoteDeploymentRecord created = remoteDeploymentService.create(principal, request.robotId(), request.notes());
        return ResponseEntity.status(201).body(ApiResponse.ok(created));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('ROBOT_CONFIGURE')")
    @Operation(summary = "Record the operator's own follow-up outcome (COMPLETED/FAILED) for a deployment")
    public ApiResponse<RemoteDeploymentRecord> updateStatus(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateRemoteDeploymentStatusRequest request) {
        return ApiResponse.ok(remoteDeploymentService.updateStatus(principal, id, request.status()));
    }
}
