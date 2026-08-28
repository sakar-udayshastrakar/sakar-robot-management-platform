package com.sakarrobotics.cloud.org;

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

import com.sakarrobotics.cloud.common.error.ApiException;
import com.sakarrobotics.cloud.common.error.SakarErrorCode;
import com.sakarrobotics.cloud.common.web.ApiResponse;
import com.sakarrobotics.cloud.org.dto.CreateOrganizationRequest;
import com.sakarrobotics.cloud.security.UserPrincipal;
import com.sakarrobotics.cloud.security.access.TenantAccessGuard;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Organization hierarchy management (Master Requirements Part 7 "Organization
 * Management", extended per the implementation brief's distributor/
 * sub-distributor/client tree — see {@link OrganizationType}).
 */
@RestController
@RequestMapping("/api/v1/organizations")
@RequiredArgsConstructor
@Tag(name = "Organizations")
public class OrganizationController {

    private final OrganizationService organizationService;
    private final OrganizationRepository organizationRepository;
    private final TenantAccessGuard tenantAccessGuard;

    @PostMapping
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    @Operation(summary = "Create an organization (optionally as a child of an existing one, forming the distributor/client hierarchy)")
    public ResponseEntity<ApiResponse<Organization>> create(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateOrganizationRequest request) {
        if (request.parentOrganizationId() != null) {
            tenantAccessGuard.assertOrganizationAccess(principal, request.parentOrganizationId());
        } else if (!principal.isSuperAdmin()) {
            throw new ApiException(SakarErrorCode.FORBIDDEN, "Only SUPER_ADMIN may create a root-level organization");
        }
        Organization created = organizationService.create(request.name(), request.orgType(), request.parentOrganizationId());
        return ResponseEntity.status(201).body(ApiResponse.ok(created));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ROBOT_VIEW')")
    @Operation(summary = "Get an organization the caller is authorized to see")
    public ApiResponse<Organization> get(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        tenantAccessGuard.assertOrganizationAccess(principal, id);
        return ApiResponse.ok(organizationService.getOrThrow(id));
    }

    @GetMapping("/{id}/children")
    @PreAuthorize("hasAuthority('ROBOT_VIEW')")
    @Operation(summary = "List the direct children of an organization (distributor -> sub-distributor -> client tree)")
    public ApiResponse<List<Organization>> children(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        tenantAccessGuard.assertOrganizationAccess(principal, id);
        return ApiResponse.ok(organizationRepository.findByParentOrganizationId(id));
    }
}
