package com.sakarrobotics.cloud.repair;

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
import com.sakarrobotics.cloud.repair.dto.CreateRepairRequestRequest;
import com.sakarrobotics.cloud.repair.dto.UpdateRepairRequestStatusRequest;
import com.sakarrobotics.cloud.security.UserPrincipal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Operation And Maintenance Platform → Customer Repair Requests (real work-order log). */
@RestController
@RequestMapping("/api/v1/repair-requests")
@RequiredArgsConstructor
@Tag(name = "Repair Requests")
public class RepairRequestController {

    private final RepairRequestService repairRequestService;

    @GetMapping
    @PreAuthorize("hasAuthority('ROBOT_VIEW')")
    @Operation(summary = "List repair requests within the caller's organization scope")
    public ApiResponse<List<RepairRequest>> list(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(repairRequestService.listAccessible(principal));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ROBOT_CONFIGURE')")
    @Operation(summary = "Log a customer repair request (work order)")
    public ResponseEntity<ApiResponse<RepairRequest>> create(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateRepairRequestRequest request) {
        RepairRequest created = repairRequestService.create(principal, request.organizationId(), request.siteId(),
                request.robotId(), request.symptom(), request.reportedBy(), request.notes());
        return ResponseEntity.status(201).body(ApiResponse.ok(created));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('ROBOT_CONFIGURE')")
    @Operation(summary = "Update a repair request's status")
    public ApiResponse<RepairRequest> updateStatus(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateRepairRequestStatusRequest request) {
        return ApiResponse.ok(repairRequestService.updateStatus(principal, id, request.status(), request.notes()));
    }
}
